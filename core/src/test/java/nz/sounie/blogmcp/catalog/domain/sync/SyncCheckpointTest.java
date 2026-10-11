package nz.sounie.blogmcp.catalog.domain.sync;

import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncCheckpointTest {

  private static final Instant CHECKPOINT = Instant.parse("2026-09-20T01:20:47Z");
  private static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");

  @Test
  @DisplayName("AC-CAT-1: with no checkpoint the next sync fetches everything")
  void a_new_checkpoint_asks_for_everything() {
    SyncCheckpoint checkpoint = SyncCheckpoint.start(SOUNIE_WP_ID);

    assertThat(checkpoint.changesSinceForNextSync(OverlapMargin.STANDARD)).isEmpty();
  }

  @Test
  @DisplayName("AC-CAT-5: the next sync starts one hour before the checkpoint")
  void next_sync_starts_one_overlap_margin_before_the_checkpoint() {
    SyncCheckpoint checkpoint = SyncCheckpoint.start(SOUNIE_WP_ID);
    checkpoint.advanceTo(CHECKPOINT);

    assertThat(checkpoint.changesSinceForNextSync(OverlapMargin.STANDARD))
        .contains(Instant.parse("2026-09-20T00:20:47Z"));
  }

  @Test
  void advances_to_a_later_source_timestamp() {
    SyncCheckpoint checkpoint = SyncCheckpoint.start(SOUNIE_WP_ID);

    checkpoint.advanceTo(CHECKPOINT);
    checkpoint.advanceTo(CHECKPOINT.plusSeconds(1));

    assertThat(checkpoint.changesSeenUpTo()).contains(CHECKPOINT.plusSeconds(1));
  }

  @Test
  void never_moves_backwards() {
    SyncCheckpoint checkpoint = SyncCheckpoint.start(SOUNIE_WP_ID);
    checkpoint.advanceTo(CHECKPOINT);

    checkpoint.advanceTo(CHECKPOINT.minusSeconds(1));

    assertThat(checkpoint.changesSeenUpTo()).contains(CHECKPOINT);
  }

  @Test
  void advancing_to_the_same_timestamp_keeps_it() {
    SyncCheckpoint checkpoint =
        SyncCheckpoint.restore(SOUNIE_WP_ID, Optional.of(CHECKPOINT), Optional.empty());

    checkpoint.advanceTo(CHECKPOINT);

    assertThat(checkpoint.changesSeenUpTo()).contains(CHECKPOINT);
  }

  @Test
  void marking_reconciled_records_the_time() {
    SyncCheckpoint checkpoint = SyncCheckpoint.start(SOUNIE_WP_ID);

    checkpoint.markReconciled(NOW);

    assertThat(checkpoint.lastReconciledAt()).contains(NOW);
  }

  @Test
  void marking_reconciled_does_not_move_the_change_checkpoint() {
    SyncCheckpoint checkpoint =
        SyncCheckpoint.restore(SOUNIE_WP_ID, Optional.of(CHECKPOINT), Optional.empty());

    checkpoint.markReconciled(NOW);

    assertThat(checkpoint.changesSeenUpTo()).contains(CHECKPOINT);
  }

  @Test
  @DisplayName("AC-CAT-28: a reconcile is due when there has never been one")
  void reconcile_is_due_when_never_reconciled() {
    assertThat(SyncCheckpoint.start(SOUNIE_WP_ID).isReconcileDue(NOW)).isTrue();
  }

  @Test
  @DisplayName("AC-CAT-28: a reconcile is due 25 hours after the last one")
  void reconcile_is_due_more_than_24_hours_after_the_last_one() {
    SyncCheckpoint checkpoint =
        SyncCheckpoint.restore(
            SOUNIE_WP_ID, Optional.empty(), Optional.of(NOW.minus(Duration.ofHours(25))));

    assertThat(checkpoint.isReconcileDue(NOW)).isTrue();
  }

  @Test
  @DisplayName("AC-CAT-28: a reconcile is not due 2 hours after the last one")
  void reconcile_is_not_due_2_hours_after_the_last_one() {
    SyncCheckpoint checkpoint =
        SyncCheckpoint.restore(
            SOUNIE_WP_ID, Optional.empty(), Optional.of(NOW.minus(Duration.ofHours(2))));

    assertThat(checkpoint.isReconcileDue(NOW)).isFalse();
  }

  @Test
  void reconcile_due_boundary_is_24_hours() {
    SyncCheckpoint justInside =
        SyncCheckpoint.restore(
            SOUNIE_WP_ID,
            Optional.empty(),
            Optional.of(NOW.minus(Duration.ofHours(24)).plusSeconds(1)));
    SyncCheckpoint justOutside =
        SyncCheckpoint.restore(
            SOUNIE_WP_ID,
            Optional.empty(),
            Optional.of(NOW.minus(Duration.ofHours(24)).minusSeconds(1)));

    assertThat(justInside.isReconcileDue(NOW)).isFalse();
    assertThat(justOutside.isReconcileDue(NOW)).isTrue();
  }
}
