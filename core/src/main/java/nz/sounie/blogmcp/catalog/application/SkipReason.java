package nz.sounie.blogmcp.catalog.application;

/** Why a source entry was not applied. */
public enum SkipReason {
  MALFORMED,
  NOT_PUBLIC,
  DUPLICATE_CANONICAL_URL
}
