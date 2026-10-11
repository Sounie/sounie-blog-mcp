package nz.sounie.blogmcp.search.application;

import java.util.List;
import java.util.Objects;
import nz.sounie.blogmcp.search.domain.index.IndexDecision;
import nz.sounie.blogmcp.search.domain.index.IndexWork;
import nz.sounie.blogmcp.search.domain.index.PostIndexer;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.reconcile.ReconcilePlan;

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
    return lock.locked(this::reconcile);
  }

  /** Read the catalog's entries, plan, then apply each decision in turn. */
  private ReconcileReport reconcile() {
    ReconcilePlan plan =
        ReconcilePlan.between(catalog.currentPosts(), work.index(), work.indexer().recipe());
    List<PostOutcome> results = plan.decisions().stream().map(this::applyIsolated).toList();
    return ReconcileReport.of(results, plan.orphanRemovalSuppressedBy());
  }

  /** One post's failure (e.g. the embedder is unavailable) must not stop the others (AC-30). */
  private PostOutcome applyIsolated(IndexDecision decision) {
    try {
      return PostOutcome.applied(decision, decision.apply(work));
    } catch (RuntimeException e) {
      return PostOutcome.failed(decision, e);
    }
  }
}
