package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import nz.sounie.blogmcp.catalog.application.SyncMode;

/**
 * One background run of steps, in sequence on one thread (app.md 3.4). The startup run reconciles,
 * syncs in the startup mode, then reconciles; every later (scheduled) run syncs {@code
 * INCREMENTAL}, then reconciles. Each step's failure is caught and logged, and the next step still
 * runs.
 */
public final class SyncAndReconcile implements Runnable {

  /** A named step of a run. */
  private record Step(String name, Runnable action) {}

  private final PrintStream errors;
  private final List<Step> scheduledRun;
  private final AtomicReference<List<Step>> nextRun;

  /**
   * @param sync the sync step, given the mode ({@code SyncAllSites::run} in production)
   * @param reconcile the reconcile step ({@code ReconcileIndex::run} in production)
   * @param firstRunMode {@code StorageHealth.startupSyncMode()}
   */
  public SyncAndReconcile(
      Consumer<SyncMode> sync, Runnable reconcile, SyncMode firstRunMode, PrintStream errors) {
    Objects.requireNonNull(sync, "sync");
    Objects.requireNonNull(firstRunMode, "firstRunMode");
    Step reconcileStep = new Step("reconcile", Objects.requireNonNull(reconcile, "reconcile"));
    this.errors = Objects.requireNonNull(errors, "errors");
    this.scheduledRun =
        List.of(new Step("sync", () -> sync.accept(SyncMode.INCREMENTAL)), reconcileStep);
    this.nextRun =
        new AtomicReference<>(
            List.of(
                reconcileStep, new Step("sync", () -> sync.accept(firstRunMode)), reconcileStep));
  }

  @Override
  public void run() {
    nextRun.getAndSet(scheduledRun).forEach(this::isolated);
  }

  /** A failing step is logged and never stops the next step or a later run. */
  private void isolated(Step step) {
    try {
      step.action().run();
    } catch (RuntimeException e) {
      errors.println("blog-mcp: the " + step.name() + " step failed: " + e);
    }
  }
}
