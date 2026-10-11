package nz.sounie.blogmcp.catalog.adapter.out;

import nz.sounie.blogmcp.catalog.application.SyncMode;

/**
 * Whether loading the catalog's stored posts found any unreadable file. The composition root reads
 * it to choose the startup sync mode (docs/domain/app.md 3.3).
 */
public enum StorageHealth {
  HEALTHY(SyncMode.INCREMENTAL),
  DAMAGED(SyncMode.RECONCILE);

  private final SyncMode startupSyncMode;

  StorageHealth(SyncMode startupSyncMode) {
    this.startupSyncMode = startupSyncMode;
  }

  /** Healthy when no stored file had to be quarantined. */
  static StorageHealth afterQuarantining(int unreadableFiles) {
    return unreadableFiles == 0 ? HEALTHY : DAMAGED;
  }

  /**
   * {@code INCREMENTAL} when healthy; {@code RECONCILE} when damaged, so quarantined posts are
   * fetched again.
   */
  public SyncMode startupSyncMode() {
    return startupSyncMode;
  }
}
