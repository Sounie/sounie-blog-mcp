package nz.sounie.blogmcp.catalog.application;

import static nz.sounie.blogmcp.catalog.domain.TestSites.SOUNIE_WP_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncModeTest {

  private static final SyncCheckpoint CHECKPOINT =
      SyncCheckpoint.restore(
          SOUNIE_WP_ID, Optional.of(Instant.parse("2026-09-20T01:20:47Z")), Optional.empty());

  @Test
  @DisplayName("AC-CAT-5: an incremental listing starts one overlap margin before the checkpoint")
  void incremental_starts_from_the_checkpoint_minus_the_margin() {
    assertThat(SyncMode.INCREMENTAL.changedSince(CHECKPOINT))
        .contains(Instant.parse("2026-09-20T00:20:47Z"));
  }

  @Test
  @DisplayName("AC-CAT-1: an incremental listing without a checkpoint fetches everything")
  void incremental_without_a_checkpoint_fetches_everything() {
    assertThat(SyncMode.INCREMENTAL.changedSince(SyncCheckpoint.start(SOUNIE_WP_ID))).isEmpty();
  }

  @Test
  @DisplayName("AC-CAT-21: a reconcile fetches everything whatever the checkpoint")
  void reconcile_fetches_everything() {
    assertThat(SyncMode.RECONCILE.changedSince(CHECKPOINT)).isEmpty();
  }

  @Test
  void only_a_reconcile_runs_the_reconcile_step_after_a_complete_run() {
    AtomicInteger reconciles = new AtomicInteger();

    SyncMode.INCREMENTAL.afterCompleteRun(reconciles::incrementAndGet);
    assertThat(reconciles).hasValue(0);

    SyncMode.RECONCILE.afterCompleteRun(reconciles::incrementAndGet);
    assertThat(reconciles).hasValue(1);
  }
}
