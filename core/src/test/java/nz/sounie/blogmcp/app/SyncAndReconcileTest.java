package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import nz.sounie.blogmcp.catalog.application.SyncMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * AC-APP-10, AC-APP-50: the startup run reconciles, syncs in the startup mode, then reconciles;
 * every later run syncs INCREMENTAL, then reconciles. Failures are logged and never stop later
 * steps or runs.
 */
class SyncAndReconcileTest {

  private final List<String> steps = new CopyOnWriteArrayList<>();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(stderr, true, StandardCharsets.UTF_8);
  private final AtomicInteger reconcileCalls = new AtomicInteger();
  private RuntimeException syncFailure;
  private RuntimeException reconcileFailure;
  private int failingReconcileCall;

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
          int call = reconcileCalls.incrementAndGet();
          if (reconcileFailure != null
              && (failingReconcileCall == 0 || failingReconcileCall == call)) {
            throw reconcileFailure;
          }
        },
        firstRunMode,
        errors);
  }

  private String stderr() {
    return stderr.toString(StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @EnumSource(SyncMode.class)
  @DisplayName(
      "AC-APP-50, AC-APP-9: the startup run reconciles, syncs in the startup mode, then reconciles;"
          + " the next run syncs INCREMENTAL, then reconciles")
  void the_startup_run_opens_with_a_reconcile_and_later_runs_are_scheduled(SyncMode startupMode) {
    SyncAndReconcile job = jobStartingIn(startupMode);

    job.run();
    job.run();

    assertThat(steps)
        .containsExactly(
            "reconcile", "sync " + startupMode, "reconcile", "sync INCREMENTAL", "reconcile");
  }

  @Test
  @DisplayName(
      "AC-APP-50: a failing startup reconcile is logged, and the sync and second reconcile still run")
  void a_failing_startup_reconcile_is_logged_and_the_run_continues() {
    reconcileFailure = new IllegalStateException("index unreadable");
    failingReconcileCall = 1;
    SyncAndReconcile job = jobStartingIn(SyncMode.INCREMENTAL);

    assertThatCode(job::run).doesNotThrowAnyException();

    assertThat(steps).containsExactly("reconcile", "sync INCREMENTAL", "reconcile");
    assertThat(stderr()).contains("index unreadable");
  }

  @Test
  @DisplayName("AC-APP-10: a failing sync is logged, and the reconcile still runs")
  void a_failing_sync_is_logged_and_the_reconcile_still_runs() {
    syncFailure = new IllegalStateException("site down");
    SyncAndReconcile job = jobStartingIn(SyncMode.INCREMENTAL);

    job.run();
    assertThatCode(job::run).doesNotThrowAnyException();

    assertThat(steps)
        .containsExactly(
            "reconcile", "sync INCREMENTAL", "reconcile", "sync INCREMENTAL", "reconcile");
    assertThat(stderr()).contains("site down");
  }

  @Test
  @DisplayName("AC-APP-10: a failing reconcile is logged, and the next run still happens")
  void a_failing_reconcile_is_logged_and_does_not_stop_later_runs() {
    reconcileFailure = new IllegalStateException("index broken");
    SyncAndReconcile job = jobStartingIn(SyncMode.INCREMENTAL);

    assertThatCode(job::run).doesNotThrowAnyException();
    assertThatCode(job::run).doesNotThrowAnyException();
    reconcileFailure = null;
    job.run();

    assertThat(steps)
        .containsExactly(
            "reconcile",
            "sync INCREMENTAL",
            "reconcile",
            "sync INCREMENTAL",
            "reconcile",
            "sync INCREMENTAL",
            "reconcile");
    assertThat(stderr()).contains("index broken");
  }
}
