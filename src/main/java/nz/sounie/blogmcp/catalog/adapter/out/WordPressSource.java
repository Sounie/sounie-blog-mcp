package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import nz.sounie.blogmcp.catalog.domain.SourceUnavailable;
import nz.sounie.blogmcp.catalog.domain.Tag;
import nz.sounie.blogmcp.catalog.domain.Title;
import tools.jackson.databind.JsonNode;

/**
 * Reads posts from the WordPress REST API ({@code /wp-json/wp/v2/posts}, ordered by modification
 * time ascending) and resolves tag IDs through {@code /wp-json/wp/v2/tags}. Categories are never
 * requested. The cursor is the WordPress page number.
 */
public final class WordPressSource implements BlogSource {

  private static final String POSTS = "/wp-json/wp/v2/posts";
  private static final String TAGS = "/wp-json/wp/v2/tags";
  private static final int PER_PAGE = 100;
  private static final String TOTAL_PAGES = "X-WP-TotalPages";

  private final HtmlToText htmlToText;
  private final Function<Site, URI> apiBase;
  private final JsonHttp http;

  /** Calls the API under each site's base URL. */
  public WordPressSource(HttpClient httpClient, HtmlToText htmlToText) {
    this(httpClient, htmlToText, Site::baseUrl);
  }

  /**
   * @param apiBase where to send requests for a site (the site's base URL in production; a local
   *     stub server in tests)
   */
  public WordPressSource(
      HttpClient httpClient, HtmlToText htmlToText, Function<Site, URI> apiBase) {
    this.http = new JsonHttp(httpClient);
    this.htmlToText = htmlToText;
    this.apiBase = apiBase;
  }

  @Override
  public ChangeOrder changeOrder() {
    return ChangeOrder.OLDEST_FIRST;
  }

  @Override
  public SourcePage fetch(Site site, Optional<Instant> changedSince, PageCursor cursor) {
    URI base = apiBase.apply(site);
    Map<String, String> query = new LinkedHashMap<>();
    query.put("per_page", String.valueOf(PER_PAGE));
    query.put("page", String.valueOf(cursor.value()));
    query.put("orderby", "modified");
    query.put("order", "asc");
    // An explicit UTC offset: WordPress reads a zone-less value as the site's local time.
    changedSince.ifPresent(
        since -> query.put("modified_after", DateTimeFormatter.ISO_INSTANT.format(since)));

    JsonHttp.Response response = http.get(base, POSTS, query);
    if (!response.body().isArray()) {
      throw new SourceUnavailable("WordPress posts response is not a JSON array");
    }
    List<JsonNode> posts = response.body().values().stream().toList();
    Map<Long, String> tagNames = tagNames(base, tagIdsOf(posts));
    List<SourceEntry> entries =
        posts.stream().map(post -> new EntryMapping(site, post, tagNames).entry()).toList();
    OptionalInt totalPages = JsonHttp.count(response.headers().firstValue(TOTAL_PAGES));
    return new SourcePage(entries, nextCursor(cursor, posts.size(), totalPages));
  }

  /**
   * The next page number while {@code X-WP-TotalPages} says more follow. Without a usable total,
   * keeps paging while pages are full, so a truncated listing is never reported as complete.
   */
  private static Optional<PageCursor> nextCursor(
      PageCursor cursor, int received, OptionalInt totalPages) {
    boolean more =
        totalPages.isPresent() ? cursor.value() < totalPages.getAsInt() : received >= PER_PAGE;
    return more ? Optional.of(new PageCursor(cursor.value() + 1)) : Optional.empty();
  }

  private static Set<Long> tagIdsOf(List<JsonNode> posts) {
    Set<Long> ids = new LinkedHashSet<>();
    for (JsonNode post : posts) {
      for (JsonNode tag : post.path("tags").values()) {
        if (tag.canConvertToLong()) {
          ids.add(tag.longValue());
        }
      }
    }
    return ids;
  }

  /** Looks up the names of the given tag IDs, at most one page of {@value #PER_PAGE} at a time. */
  private Map<Long, String> tagNames(URI base, Set<Long> ids) {
    Map<Long, String> names = new HashMap<>();
    List<Long> remaining = new ArrayList<>(ids);
    while (!remaining.isEmpty()) {
      List<Long> batch = remaining.subList(0, Math.min(PER_PAGE, remaining.size()));
      Map<String, String> query = new LinkedHashMap<>();
      query.put("include", batch.stream().map(String::valueOf).collect(Collectors.joining(",")));
      query.put("per_page", String.valueOf(PER_PAGE));
      for (JsonNode tag : http.get(base, TAGS, query).body().values()) {
        JsonNode name = tag.path("name");
        if (tag.path("id").canConvertToLong() && name.isString()) {
          names.put(tag.path("id").longValue(), htmlToText.decodeEntities(name.stringValue()));
        }
      }
      batch.clear();
    }
    return names;
  }

  /** Maps one post object to a source entry, never throwing for bad data. */
  private final class EntryMapping {

    private final Site site;
    private final JsonNode post;
    private final Map<Long, String> tagNames;

    EntryMapping(Site site, JsonNode post, Map<Long, String> tagNames) {
      this.site = site;
      this.post = post;
      this.tagNames = tagNames;
    }

    SourceEntry entry() {
      Optional<SourcePostId> id = sourcePostId();
      Optional<Instant> modified = gmt("modified_gmt");
      if (id.isEmpty()) {
        return new SourceEntry.Malformed(Optional.empty(), "missing or invalid id", modified);
      }
      if (modified.isEmpty()) {
        return new SourceEntry.Malformed(id, "missing or invalid modified_gmt", modified);
      }
      if (!isPublic()) {
        return new SourceEntry.NotPublic(id.get(), modified);
      }
      Optional<Instant> published = gmt("date_gmt");
      if (published.isEmpty()) {
        return new SourceEntry.Malformed(id, "missing or invalid date_gmt", modified);
      }
      CanonicalUrl url;
      try {
        url = CanonicalUrl.onSite(site, post.path("link").asString(""));
      } catch (CanonicalUrlNotOnSite e) {
        return new SourceEntry.Malformed(id, e.getMessage(), modified);
      }
      List<String> notes = new ArrayList<>();
      PostSnapshot snapshot =
          new PostSnapshot(
              new PostId(site.id(), id.get()),
              url,
              new Title(htmlToText.extract(post.path("title").path("rendered").asString(""))),
              new Body(htmlToText.extract(post.path("content").path("rendered").asString(""))),
              BodyCompleteness.FULL,
              tags(notes),
              published.get(),
              modified.get());
      return new SourceEntry.Available(snapshot, notes);
    }

    private Optional<SourcePostId> sourcePostId() {
      JsonNode id = post.path("id");
      return id.isIntegralNumber() || (id.isString() && !id.stringValue().isBlank())
          ? Optional.of(new SourcePostId(id.asString()))
          : Optional.empty();
    }

    /** WordPress {@code *_gmt} fields are zone-less but mean UTC. */
    private Optional<Instant> gmt(String field) {
      JsonNode value = post.path(field);
      if (!value.isString()) {
        return Optional.empty();
      }
      try {
        return Optional.of(LocalDateTime.parse(value.stringValue()).toInstant(ZoneOffset.UTC));
      } catch (DateTimeParseException e) {
        return Optional.empty();
      }
    }

    private boolean isPublic() {
      boolean passwordProtected = post.path("content").path("protected").asBoolean(false);
      String status = post.path("status").asString("publish");
      return !passwordProtected && status.equals("publish");
    }

    private Set<Tag> tags(List<String> notes) {
      List<String> labels = new ArrayList<>();
      for (JsonNode tagId : post.path("tags").values()) {
        String name = tagId.canConvertToLong() ? tagNames.get(tagId.longValue()) : null;
        if (name == null) {
          notes.add("unknown tag id " + tagId.asString() + " was dropped");
        } else {
          labels.add(name);
        }
      }
      return Tag.setOf(labels);
    }
  }
}
