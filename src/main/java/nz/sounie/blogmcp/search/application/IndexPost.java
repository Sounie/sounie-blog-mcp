package nz.sounie.blogmcp.search.application;

import nz.sounie.blogmcp.search.domain.IndexChange;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.PostIndexer;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/** Applies one index change: decide, then apply the decision. */
public final class IndexPost {

  public IndexPost(VectorIndex index, PostIndexer indexer, IndexWriteLock lock) {}

  /**
   * @throws nz.sounie.blogmcp.search.domain.EmbedderUnavailable if embedding fails; the index is
   *     unchanged
   */
  public IndexOutcome apply(IndexChange change) {
    throw new UnsupportedOperationException("not implemented");
  }
}
