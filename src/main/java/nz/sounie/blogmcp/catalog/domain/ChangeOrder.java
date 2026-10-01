package nz.sounie.blogmcp.catalog.domain;

/** The order in which a blog source returns changed entries. Decides when the checkpoint moves. */
public enum ChangeOrder {
  OLDEST_FIRST,
  NEWEST_FIRST,
  UNORDERED
}
