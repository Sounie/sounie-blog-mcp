package nz.sounie.blogmcp.catalog.application;

import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.OverlapMargin;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpoint;

/** Whether a sync starts from the checkpoint or is a full reconcile. */
public enum SyncMode {
  INCREMENTAL {
    @Override
    Optional<Instant> changedSince(SyncCheckpoint checkpoint) {
      return checkpoint.changesSinceForNextSync(OverlapMargin.STANDARD);
    }
  },
  RECONCILE {
    @Override
    Optional<Instant> changedSince(SyncCheckpoint checkpoint) {
      return Optional.empty();
    }

    @Override
    void afterCompleteRun(Runnable reconcile) {
      reconcile.run();
    }
  };

  /** Where the source listing starts: from the checkpoint, or everything when empty. */
  abstract Optional<Instant> changedSince(SyncCheckpoint checkpoint);

  /** Runs the reconcile step after a complete run, in reconcile mode only. */
  void afterCompleteRun(Runnable reconcile) {
    // An incremental sync never withdraws unlisted posts.
  }
}
