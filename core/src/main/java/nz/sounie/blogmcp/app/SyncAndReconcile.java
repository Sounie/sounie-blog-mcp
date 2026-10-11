package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import nz.sounie.blogmcp.catalog.application.SyncMode;

/**
 * One background run: sync every site, then reconcile the index, in sequence on one thread. Each
 * step's failure is caught and logged, and the reconcile runs even if the sync step threw. The
 * first run syncs in the startup mode; later runs are {@code INCREMENTAL}.
 */
public final class SyncAndReconcile implements Runnable {

  private final Consumer<SyncMode> sync;
  private final Runnable reconcile;
  private final AtomicReference<SyncMode> nextMode;
  private final PrintStream errors;

  /**
   * @param sync the sync step, given the mode ({@code SyncAllSites::run} in production)
   * @param reconcile the reconcile step ({@code ReconcileIndex::run} in production)
   * @param firstRunMode {@code StorageHealth.startupSyncMode()}
   */
  public SyncAndReconcile(
      Consumer<SyncMode> sync, Runnable reconcile, SyncMode firstRunMode, PrintStream errors) {
    this.sync = Objects.requireNonNull(sync, "sync");
    this.reconcile = Objects.requireNonNull(reconcile, "reconcile");
    this.nextMode = new AtomicReference<>(Objects.requireNonNull(firstRunMode, "firstRunMode"));
    this.errors = Objects.requireNonNull(errors, "errors");
  }

  @Override
  public void run() {
    SyncMode mode = nextMode.getAndSet(SyncMode.INCREMENTAL);
    isolated("sync", () -> sync.accept(mode));
    isolated("reconcile", reconcile);
  }

  /** A failing step is logged and never stops the next step or a later run. */
  private void isolated(String step, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      errors.println("blog-mcp: the " + step + " step failed: " + e);
    }
  }
}
