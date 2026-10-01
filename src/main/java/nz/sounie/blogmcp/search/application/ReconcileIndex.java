package nz.sounie.blogmcp.search.application;

import nz.sounie.blogmcp.search.domain.PostIndexer;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/**
 * Compares the catalog's current posts with the index and applies the plan one post at a time. A
 * failing post is {@code FAILED}; the others still go ahead.
 */
public final class ReconcileIndex {

  public ReconcileIndex(
      PostCatalog catalog, VectorIndex index, PostIndexer indexer, IndexWriteLock lock) {}

  public ReconcileReport run() {
    throw new UnsupportedOperationException("not implemented");
  }
}
