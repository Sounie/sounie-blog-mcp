package nz.sounie.blogmcp.search.application;

import nz.sounie.blogmcp.search.domain.Embedder;
import nz.sounie.blogmcp.search.domain.SearchQuery;
import nz.sounie.blogmcp.search.domain.SearchResults;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/** Answers a search query. Does not take the index write lock. */
public final class SearchPosts {

  public SearchPosts(VectorIndex index, Embedder embedder) {}

  public SearchResults search(SearchQuery query) {
    throw new UnsupportedOperationException("not implemented");
  }
}
