package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.Body;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrlNotOnSite;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePage;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.Tag;
import nz.sounie.blogmcp.catalog.domain.Title;
import tools.jackson.databind.JsonNode;

/**
 * Reads posts from the Blogger JSON feed ({@code /feeds/posts/default?alt=json}). The cursor is the
 * 1-based {@code start-index}. Uses {@code content} (completeness {@code FULL}) when present,
 * otherwise {@code summary} ({@code SUMMARY}).
 */
public final class BloggerSource implements BlogSource {

  private static final String FEED = "/feeds/posts/default";
  private static final int MAX_RESULTS = 150;
  private static final Pattern POST_ID = Pattern.compile("\\.post-(\\d+)$");
  private static final String TEXT = "$t";

  private final HtmlToText htmlToText;
  private final Function<Site, URI> apiBase;
  private final JsonHttp http;

  /** Calls the feed under each site's base URL. */
  public BloggerSource(HttpClient httpClient, HtmlToText htmlToText) {
    this(httpClient, htmlToText, Site::baseUrl);
  }

  /**
   * @param apiBase where to send requests for a site (the site's base URL in production; a local
   *     stub server in tests)
   */
  public BloggerSource(HttpClient httpClient, HtmlToText htmlToText, Function<Site, URI> apiBase) {
    this.http = new JsonHttp(httpClient);
    this.htmlToText = htmlToText;
    this.apiBase = apiBase;
  }

  @Override
  public ChangeOrder changeOrder() {
    return ChangeOrder.NEWEST_FIRST;
  }

  @Override
  public SourcePage fetch(Site site, Optional<Instant> changedSince, PageCursor cursor) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("alt", "json");
    query.put("max-results", String.valueOf(MAX_RESULTS));
    query.put("start-index", String.valueOf(cursor.value()));
    query.put("orderby", "updated");
    changedSince.ifPresent(
        since -> query.put("updated-min", DateTimeFormatter.ISO_INSTANT.format(since)));

    JsonNode feed = http.get(apiBase.apply(site), FEED, query).body().path("feed");
    List<JsonNode> entries = feed.path("entry").values().stream().toList();
    List<SourceEntry> mapped =
        entries.stream().map(entry -> new EntryMapping(site, entry).entry()).toList();
    return new SourcePage(mapped, nextCursor(cursor, entries.size(), totalResults(feed)));
  }

  /** Paging stops on a short page, or once the start index passes the total number of results. */
  private static Optional<PageCursor> nextCursor(PageCursor cursor, int received, int total) {
    int nextIndex = cursor.value() + received;
    return received < MAX_RESULTS || nextIndex > total
        ? Optional.empty()
        : Optional.of(new PageCursor(nextIndex));
  }

  private static int totalResults(JsonNode feed) {
    try {
      return Integer.parseInt(feed.path("openSearch$totalResults").path(TEXT).asString("0"));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String text(JsonNode node) {
    return node.path(TEXT).asString("");
  }

  /** Maps one feed entry to a source entry, never throwing for bad data. */
  private final class EntryMapping {

    private final Site site;
    private final JsonNode entry;

    EntryMapping(Site site, JsonNode entry) {
      this.site = site;
      this.entry = entry;
    }

    SourceEntry entry() {
      Optional<SourcePostId> id = sourcePostId();
      Optional<Instant> updated = instant("updated");
      if (id.isEmpty()) {
        return new SourceEntry.Malformed(Optional.empty(), "missing or invalid id", updated);
      }
      if (updated.isEmpty()) {
        return new SourceEntry.Malformed(id, "missing or invalid updated time", updated);
      }
      Optional<Instant> published = instant("published");
      if (published.isEmpty()) {
        return new SourceEntry.Malformed(id, "missing or invalid published time", updated);
      }
      CanonicalUrl url;
      try {
        url = CanonicalUrl.onSite(site, alternateLink());
      } catch (CanonicalUrlNotOnSite e) {
        return new SourceEntry.Malformed(id, e.getMessage(), updated);
      }
      boolean hasContent = entry.path("content").has(TEXT);
      String html = hasContent ? text(entry.path("content")) : text(entry.path("summary"));
      return new SourceEntry.Available(
          new PostSnapshot(
              new PostId(site.id(), id.get()),
              url,
              new Title(htmlToText.decodeEntities(text(entry.path("title")))),
              new Body(htmlToText.extract(html)),
              hasContent ? BodyCompleteness.FULL : BodyCompleteness.SUMMARY,
              Tag.setOf(labels()),
              published.get(),
              updated.get()));
    }

    /** The digits after {@code .post-} in the entry ID. */
    private Optional<SourcePostId> sourcePostId() {
      Matcher matcher = POST_ID.matcher(text(entry.path("id")));
      return matcher.find() ? Optional.of(new SourcePostId(matcher.group(1))) : Optional.empty();
    }

    private Optional<Instant> instant(String field) {
      try {
        return Optional.of(OffsetDateTime.parse(text(entry.path(field))).toInstant());
      } catch (DateTimeParseException e) {
        return Optional.empty();
      }
    }

    private String alternateLink() {
      for (JsonNode link : entry.path("link").values()) {
        if (link.path("rel").asString("").equals("alternate")) {
          return link.path("href").asString("");
        }
      }
      return "";
    }

    private List<String> labels() {
      List<String> labels = new ArrayList<>();
      for (JsonNode category : entry.path("category").values()) {
        labels.add(category.path("term").asString(""));
      }
      return labels;
    }
  }
}
