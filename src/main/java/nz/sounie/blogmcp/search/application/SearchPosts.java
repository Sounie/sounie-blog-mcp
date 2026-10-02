package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.Embedder;
import nz.sounie.blogmcp.search.domain.Embedding;
import nz.sounie.blogmcp.search.domain.QueryPassage;
import nz.sounie.blogmcp.search.domain.SearchFilters;
import nz.sounie.blogmcp.search.domain.SearchQuery;
import nz.sounie.blogmcp.search.domain.SearchResults;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/** Answers a search query. Does not take the index write lock. */
public final class SearchPosts {

  private final VectorIndex index;
  private final Embedder embedder;

  public SearchPosts(VectorIndex index, Embedder embedder) {
    this.index = Objects.requireNonNull(index, "index");
    this.embedder = Objects.requireNonNull(embedder, "embedder");
  }

  public SearchResults search(SearchQuery query) {
    Embedding embedded = embedder.embedQuery(QueryPassage.of(query.text()));
    SearchFilters filters = query.filters();
    return SearchResults.rank(
        index
            .all()
            .filter(post -> filters.includes(post.metadata()))
            .flatMap(post -> post.bestMatch(embedded).stream()),
        query.limit());
  }
}
