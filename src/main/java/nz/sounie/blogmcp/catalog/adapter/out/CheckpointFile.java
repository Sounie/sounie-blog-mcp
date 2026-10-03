package nz.sounie.blogmcp.catalog.adapter.out;

import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;

/**
 * A stored sync checkpoint as JSON, and the immutable snapshot the repository keeps in memory. The
 * two instants are optional ({@code null} in the file when absent).
 */
record CheckpointFile(String siteId, String changesSeenUpTo, String lastReconciledAt) {

  static CheckpointFile of(SyncCheckpoint checkpoint) {
    return new CheckpointFile(
        checkpoint.siteId().value(),
        checkpoint.changesSeenUpTo().map(Instant::toString).orElse(null),
        checkpoint.lastReconciledAt().map(Instant::toString).orElse(null));
  }

  /**
   * This file, if it restores a valid checkpoint.
   *
   * @throws RuntimeException naming the problem, if it does not
   */
  CheckpointFile validated() {
    toCheckpoint();
    return this;
  }

  /** A fresh checkpoint, restored from this snapshot. */
  SyncCheckpoint toCheckpoint() {
    return SyncCheckpoint.restore(site(), instant(changesSeenUpTo), instant(lastReconciledAt));
  }

  SiteId site() {
    return new SiteId(siteId);
  }

  private static Optional<Instant> instant(String text) {
    return Optional.ofNullable(text).map(Instant::parse);
  }
}
