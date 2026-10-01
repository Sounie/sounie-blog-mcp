package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Optional;

/** Per-site high-water mark of handled changes, based on source timestamps. Aggregate root. */
public final class SyncCheckpoint {

  private final SiteId siteId;
  private Optional<Instant> changesSeenUpTo;
  private Optional<Instant> lastReconciledAt;

  private SyncCheckpoint(
      SiteId siteId, Optional<Instant> changesSeenUpTo, Optional<Instant> lastReconciledAt) {
    this.siteId = siteId;
    this.changesSeenUpTo = changesSeenUpTo;
    this.lastReconciledAt = lastReconciledAt;
  }

  /** A checkpoint for a site that has never been synced. */
  public static SyncCheckpoint start(SiteId siteId) {
    return new SyncCheckpoint(siteId, Optional.empty(), Optional.empty());
  }

  /** Rebuilds a stored checkpoint, for repositories. Performs no business logic. */
  public static SyncCheckpoint restore(
      SiteId siteId, Optional<Instant> changesSeenUpTo, Optional<Instant> lastReconciledAt) {
    return new SyncCheckpoint(siteId, changesSeenUpTo, lastReconciledAt);
  }

  /** Moves {@code changesSeenUpTo} forward. An earlier or equal value is a no-op. */
  public void advanceTo(Instant sourceTimestamp) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** {@code changesSeenUpTo - margin}, or empty (full fetch) if there is no checkpoint yet. */
  public Optional<Instant> changesSinceForNextSync(OverlapMargin margin) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Records a completed reconcile. */
  public void markReconciled(Instant now) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** A reconcile is due if there has never been one, or the last was more than 24 hours ago. */
  public boolean isReconcileDue(Instant now) {
    throw new UnsupportedOperationException("not implemented");
  }

  public SiteId siteId() {
    return siteId;
  }

  public Optional<Instant> changesSeenUpTo() {
    return changesSeenUpTo;
  }

  public Optional<Instant> lastReconciledAt() {
    return lastReconciledAt;
  }
}
