package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import nz.sounie.blogmcp.catalog.application.SyncMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-APP-10: one run syncs, then reconciles; failures are logged and never stop later runs. */
class SyncAndReconcileTest {

  private final List<String> steps = new CopyOnWriteArrayList<>();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(stderr, true, StandardCharsets.UTF_8);
  private RuntimeException syncFailure;
  private RuntimeException reconcileFailure;

  private SyncAndReconcile jobStartingIn(SyncMode firstRunMode) {
    return new SyncAndReconcile(
        mode -> {
          steps.add("sync " + mode);
          if (syncFailure != null) {
            throw syncFailure;
          }
        },
        () -> {
          steps.add("reconcile");
          if (reconcileFailure != null) {
            throw reconcileFailure;
          }
        },
        firstRunMode,
        errors);
  }

  private String stderr() {
    return stderr.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("AC-APP-10: each run syncs first, then reconciles; later runs are INCREMENTAL")
  void syncs_then_reconciles_and_later_runs_are_incremental() {
    SyncAndReconcile job = jobStartingIn(SyncMode.RECONCILE);

    job.run();
    job.run();
    job.run();

    assertThat(steps)
        .containsExactly(
            "sync RECONCILE",
            "reconcile",
            "sync INCREMENTAL",
            "reconcile",
            "sync INCREMENTAL",
            "reconcile");
  }

  @Test
  @DisplayName("AC-APP-9: a healthy store starts with an INCREMENTAL run")
  void the_first_run_uses_the_startup_mode() {
    jobStartingIn(SyncMode.INCREMENTAL).run();

    assertThat(steps).containsExactly("sync INCREMENTAL", "reconcile");
  }

  @Test
  @DisplayName("AC-APP-10: a failing sync is logged, and the reconcile still runs")
  void a_failing_sync_is_logged_and_the_reconcile_still_runs() {
    syncFailure = new IllegalStateException("site down");
    SyncAndReconcile job = jobStartingIn(SyncMode.INCREMENTAL);

    assertThatCode(job::run).doesNotThrowAnyException();

    assertThat(steps).containsExactly("sync INCREMENTAL", "reconcile");
    assertThat(stderr()).contains("site down");
  }

  @Test
  @DisplayName("AC-APP-10: a failing reconcile is logged, and the next run still happens")
  void a_failing_reconcile_is_logged_and_does_not_stop_later_runs() {
    reconcileFailure = new IllegalStateException("index broken");
    SyncAndReconcile job = jobStartingIn(SyncMode.INCREMENTAL);

    assertThatCode(job::run).doesNotThrowAnyException();
    reconcileFailure = null;
    job.run();

    assertThat(steps)
        .containsExactly("sync INCREMENTAL", "reconcile", "sync INCREMENTAL", "reconcile");
    assertThat(stderr()).contains("index broken");
  }
}
