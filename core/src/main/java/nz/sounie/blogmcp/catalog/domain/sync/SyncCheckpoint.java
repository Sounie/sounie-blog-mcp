package nz.sounie.blogmcp.catalog.domain.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;

/** Per-site high-water mark of handled changes, based on source timestamps. Aggregate root. */
public final class SyncCheckpoint {

  /** How long after the last reconcile the next one becomes due. */
  private static final Duration RECONCILE_INTERVAL = Duration.ofHours(24);

  private final SiteId siteId;
  private Optional<Instant> changesSeenUpTo;
  private Optional<Instant> lastReconciledAt;

  private SyncCheckpoint(
      SiteId siteId, Optional<Instant> changesSeenUpTo, Optional<Instant> lastReconciledAt) {
    this.siteId = Objects.requireNonNull(siteId, "site ID");
    this.changesSeenUpTo = Objects.requireNonNull(changesSeenUpTo, "changesSeenUpTo");
    this.lastReconciledAt = Objects.requireNonNull(lastReconciledAt, "lastReconciledAt");
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
    Objects.requireNonNull(sourceTimestamp, "source timestamp");
    if (changesSeenUpTo.isEmpty() || sourceTimestamp.isAfter(changesSeenUpTo.get())) {
      changesSeenUpTo = Optional.of(sourceTimestamp);
    }
  }

  /** {@code changesSeenUpTo - margin}, or empty (full fetch) if there is no checkpoint yet. */
  public Optional<Instant> changesSinceForNextSync(OverlapMargin margin) {
    return changesSeenUpTo.map(seenUpTo -> seenUpTo.minus(margin.value()));
  }

  /** Records a completed reconcile. */
  public void markReconciled(Instant now) {
    lastReconciledAt = Optional.of(Objects.requireNonNull(now, "now"));
  }

  /** A reconcile is due if there has never been one, or the last was more than 24 hours ago. */
  public boolean isReconcileDue(Instant now) {
    return lastReconciledAt.map(last -> last.isBefore(now.minus(RECONCILE_INTERVAL))).orElse(true);
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
