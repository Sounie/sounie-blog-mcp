package nz.sounie.blogmcp.search.domain.query;

import java.util.Objects;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;

/**
 * The part of a query that is not what to look for: the site filter, the published-date range and
 * the result limit (search.md 3.8). It owns ranking, which text and image queries share.
 */
public record SearchScope(SiteFilter site, PublishedDateRange dates, ResultLimit limit) {

  public SearchScope {
    Objects.requireNonNull(site, "site");
    Objects.requireNonNull(dates, "dates");
    Objects.requireNonNull(limit, "limit");
  }

  public SearchFilters filters() {
    return SearchFilters.of(site, dates);
  }

  /**
   * The posts in scope, each scored as its best entry against the query embedding, ranked and cut
   * to the limit.
   */
  public SearchResults rank(Stream<IndexedPost> posts, Embedding query) {
    SearchFilters filters = filters();
    return SearchResults.rank(
        posts
            .filter(post -> filters.includes(post.metadata()))
            .flatMap(post -> post.bestMatch(query).stream()),
        limit);
  }
}
