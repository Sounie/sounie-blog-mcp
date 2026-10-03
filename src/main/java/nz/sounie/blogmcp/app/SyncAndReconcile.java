package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.util.function.Consumer;
import nz.sounie.blogmcp.catalog.application.SyncMode;

/**
 * One background run: sync every site, then reconcile the index, in sequence on one thread. Each
 * step's failure is caught and logged, and the reconcile runs even if the sync step threw. The
 * first run syncs in the startup mode; later runs are {@code INCREMENTAL}.
 */
public final class SyncAndReconcile implements Runnable {

  /**
   * @param sync the sync step, given the mode ({@code SyncAllSites::run} in production)
   * @param reconcile the reconcile step ({@code ReconcileIndex::run} in production)
   * @param firstRunMode {@code StorageHealth.startupSyncMode()}
   */
  public SyncAndReconcile(
      Consumer<SyncMode> sync, Runnable reconcile, SyncMode firstRunMode, PrintStream errors) {
    // red: the implementer keeps the collaborators
  }

  @Override
  public void run() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }
}
