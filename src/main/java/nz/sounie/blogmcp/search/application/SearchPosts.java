package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import java.util.function.Function;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.query.SearchFilters;
import nz.sounie.blogmcp.search.domain.query.SearchQuery;
import nz.sounie.blogmcp.search.domain.query.SearchResults;

/** Answers a search query. Does not take the index write lock. */
public final class SearchPosts {

  private final VectorIndex index;
  private final QueryEmbedder embedder;
  private final Function<QueryText, QueryPassage> prepareQuery;

  public SearchPosts(VectorIndex index, QueryEmbedder embedder) {
    this(index, embedder, QueryPassage::of);
  }

  public SearchPosts(
      VectorIndex index, QueryEmbedder embedder, Function<QueryText, QueryPassage> prepareQuery) {
    this.index = Objects.requireNonNull(index, "index");
    this.embedder = Objects.requireNonNull(embedder, "embedder");
    this.prepareQuery = Objects.requireNonNull(prepareQuery, "prepareQuery");
  }

  public SearchResults search(SearchQuery query) {
    Embedding embedded = embedder.embedQuery(prepareQuery.apply(query.text()));
    SearchFilters filters = query.filters();
    return SearchResults.rank(
        index
            .all()
            .filter(post -> filters.includes(post.metadata()))
            .flatMap(post -> post.bestMatch(embedded).stream()),
        query.limit());
  }
}
