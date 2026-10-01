package nz.sounie.blogmcp.catalog.application;

/** Whether a sync starts from the checkpoint or is a full reconcile. */
public enum SyncMode {
  INCREMENTAL,
  RECONCILE
}
