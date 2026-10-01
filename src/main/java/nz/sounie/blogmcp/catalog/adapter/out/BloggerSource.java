package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourcePage;
import nz.sounie.blogmcp.catalog.domain.SourceUnavailable;
import tools.jackson.databind.JsonNode;

/**
 * Reads posts from the Blogger JSON feed ({@code /feeds/posts/default?alt=json}). The cursor is the
 * 1-based {@code start-index}. Uses {@code content} (completeness {@code FULL}) when present,
 * otherwise {@code summary} ({@code SUMMARY}).
 */
public final class BloggerSource implements BlogSource {

  private static final String FEED = "/feeds/posts/default";
  private static final int MAX_RESULTS = 150;
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

    JsonNode feed = feedOf(http.get(apiBase.apply(site), FEED, query).body());
    List<JsonNode> entries = feed.path("entry").values().stream().toList();
    BloggerEntryMapper mapper = new BloggerEntryMapper(site, htmlToText);
    PagingRule paging =
        PagingRule.from(feed.path("openSearch$totalResults").path(TEXT).asStringOpt(), MAX_RESULTS);
    return new SourcePage(
        entries.stream().map(mapper::map).toList(), nextCursor(cursor, entries.size(), paging));
  }

  /** The {@code feed} object; anything else is not a listing, so the source is unavailable. */
  private static JsonNode feedOf(JsonNode body) {
    JsonNode feed = body.path("feed");
    requireThat(feed.isObject(), "Blogger response has no feed object");
    JsonNode entries = feed.path("entry");
    requireThat(
        entries.isMissingNode() || entries.isArray(), "Blogger feed entries are not an array");
    return feed;
  }

  private static void requireThat(boolean wellFormed, String problem) {
    if (!wellFormed) {
      throw new SourceUnavailable(problem);
    }
  }

  /**
   * The total counts entries; the listing has been read up to the last entry received. A short page
   * always ends a Blogger listing, whatever the total says.
   */
  private static Optional<PageCursor> nextCursor(
      PageCursor cursor, int received, PagingRule paging) {
    int nextIndex = cursor.value() + received;
    boolean more = received >= MAX_RESULTS && paging.hasMore(nextIndex - 1, received);
    return more ? Optional.of(new PageCursor(nextIndex)) : Optional.empty();
  }
}
