package nz.sounie.blogmcp.catalog.application;

/** How a sync ended. */
public enum SyncOutcome {
  /** Every page was handled. */
  COMPLETED,
  /** Failed after at least one page was handled. */
  PARTIAL,
  /** No page was handled. */
  FAILED,
  /** A sync for the same site was already running. */
  SKIPPED
}
