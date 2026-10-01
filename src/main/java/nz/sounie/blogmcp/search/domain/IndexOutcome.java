package nz.sounie.blogmcp.search.domain;

/** The result of applying one index decision. */
public enum IndexOutcome {
  ADDED,
  RE_EMBEDDED,
  METADATA_REFRESHED,
  UNCHANGED,
  /** An entry was deleted, by {@code Remove} or {@code Exclude}. */
  REMOVED,
  /** A {@code Remove} with no entry. */
  ALREADY_ABSENT,
  /** An {@code Exclude} with no entry. */
  EXCLUDED,
  FAILED
}
