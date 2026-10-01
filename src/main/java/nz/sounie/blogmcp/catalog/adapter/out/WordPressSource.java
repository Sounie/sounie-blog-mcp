package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourcePage;
import nz.sounie.blogmcp.catalog.domain.SourceUnavailable;
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
    List<JsonNode> posts = postsIn(response.body());
    WordPressPostMapper mapper =
        new WordPressPostMapper(site, tagNames(base, tagIdsOf(posts)), htmlToText);
    PagingRule paging = PagingRule.from(response.headers().firstValue(TOTAL_PAGES), PER_PAGE);
    return new SourcePage(
        posts.stream().map(mapper::map).toList(), nextCursor(cursor, posts, paging));
  }

  /** A posts listing is a JSON array; anything else is not a listing (catalog.md 3.4). */
  private static List<JsonNode> postsIn(JsonNode body) {
    if (!body.isArray()) {
      throw new SourceUnavailable("WordPress posts response is not a JSON array");
    }
    return body.values().stream().toList();
  }

  /** The total counts pages, so the page number is how far the listing has been read. */
  private static Optional<PageCursor> nextCursor(
      PageCursor cursor, List<JsonNode> posts, PagingRule paging) {
    return paging.hasMore(cursor.value(), posts.size())
        ? Optional.of(new PageCursor(cursor.value() + 1))
        : Optional.empty();
  }

  private static Set<Long> tagIdsOf(List<JsonNode> posts) {
    return posts.stream()
        .flatMap(post -> post.path("tags").values().stream())
        .filter(JsonNode::canConvertToLong)
        .map(JsonNode::longValue)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Looks up the names of the given tag IDs, at most {@value #PER_PAGE} IDs per request. */
  private Map<Long, String> tagNames(URI base, Set<Long> ids) {
    List<Long> all = List.copyOf(ids);
    Map<Long, String> names = new HashMap<>();
    IntStream.iterate(0, from -> from < all.size(), from -> from + PER_PAGE)
        .mapToObj(from -> all.subList(from, Math.min(from + PER_PAGE, all.size())))
        .flatMap(batch -> tagsNamed(base, batch))
        .forEach(
            tag ->
                names.put(
                    tag.path("id").longValue(),
                    htmlToText.decodeEntities(tag.path("name").stringValue())));
    return names;
  }

  /** One request for a batch of tag IDs; only well-formed tags are returned. */
  private Stream<JsonNode> tagsNamed(URI base, List<Long> batch) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("include", batch.stream().map(String::valueOf).collect(Collectors.joining(",")));
    query.put("per_page", String.valueOf(PER_PAGE));
    return http.get(base, TAGS, query).body().values().stream()
        .filter(tag -> tag.path("id").canConvertToLong() && tag.path("name").isString());
  }
}
