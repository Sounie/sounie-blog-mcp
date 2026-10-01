package nz.sounie.blogmcp.search.application;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.search.domain.IndexDecision;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.IndexWork;
import nz.sounie.blogmcp.search.domain.PostIndexer;
import nz.sounie.blogmcp.search.domain.ReconcilePlan;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/**
 * Compares the catalog's current posts with the index and applies the plan one post at a time. A
 * failing post is {@code FAILED}; the others still go ahead.
 *
 * <p>The whole reconcile holds the index write lock, so no event can change an entry between the
 * plan and its application.
 */
public final class ReconcileIndex {

  private final PostCatalog catalog;
  private final IndexWork work;
  private final IndexWriteLock lock;

  public ReconcileIndex(
      PostCatalog catalog, VectorIndex index, PostIndexer indexer, IndexWriteLock lock) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.work = new IndexWork(index, indexer);
    this.lock = Objects.requireNonNull(lock, "lock");
  }

  public ReconcileReport run() {
    return lock.locked(
        () ->
            new ReconcileReport(
                plan().decisions().stream()
                    .collect(
                        Collectors.toMap(
                            IndexDecision::postId,
                            this::applyIsolated,
                            (first, second) -> second,
                            LinkedHashMap::new))));
  }

  private ReconcilePlan plan() {
    return ReconcilePlan.between(catalog.currentPosts(), work.index(), work.indexer().recipe());
  }

  /** One post's failure (e.g. the embedder is unavailable) must not stop the others (AC-30). */
  private IndexOutcome applyIsolated(IndexDecision decision) {
    try {
      return decision.apply(work);
    } catch (RuntimeException e) {
      return IndexOutcome.FAILED;
    }
  }
}
