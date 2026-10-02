package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.IndexChange;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.IndexWork;
import nz.sounie.blogmcp.search.domain.PostIndexer;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/** Applies one index change: decide, then apply the decision. */
public final class IndexPost {

  private final IndexWork work;
  private final IndexWriteLock lock;

  public IndexPost(VectorIndex index, PostIndexer indexer, IndexWriteLock lock) {
    this.work = new IndexWork(index, indexer);
    this.lock = Objects.requireNonNull(lock, "lock");
  }

  /**
   * @throws nz.sounie.blogmcp.search.domain.EmbedderUnavailable if embedding fails; the index is
   *     unchanged
   */
  public IndexOutcome apply(IndexChange change) {
    return lock.locked(() -> change.applyTo(work));
  }
}
