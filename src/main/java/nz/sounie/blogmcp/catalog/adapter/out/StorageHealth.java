package nz.sounie.blogmcp.catalog.adapter.out;

import nz.sounie.blogmcp.catalog.application.SyncMode;

/**
 * Whether loading the catalog's stored posts found any unreadable file. The composition root reads
 * it to choose the startup sync mode (docs/domain/app.md 3.3).
 */
public enum StorageHealth {
  HEALTHY,
  DAMAGED;

  /**
   * {@code INCREMENTAL} when healthy; {@code RECONCILE} when damaged, so quarantined posts are
   * fetched again.
   */
  public SyncMode startupSyncMode() {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
