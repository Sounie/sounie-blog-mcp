package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.index.IndexChange;
import nz.sounie.blogmcp.search.domain.index.IndexOutcome;
import nz.sounie.blogmcp.search.domain.index.IndexWork;
import nz.sounie.blogmcp.search.domain.index.PostIndexer;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;

/** Applies one index change: decide, then apply the decision. */
public final class IndexPost {

  private final IndexWork work;
  private final IndexWriteLock lock;

  public IndexPost(VectorIndex index, PostIndexer indexer, IndexWriteLock lock) {
    this.work = new IndexWork(index, indexer);
    this.lock = Objects.requireNonNull(lock, "lock");
  }

  /**
   * @throws nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable if embedding fails; the
   *     index is unchanged
   */
  public IndexOutcome apply(IndexChange change) {
    return lock.locked(() -> change.applyTo(work));
  }
}
