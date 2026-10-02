package nz.sounie.blogmcp.search.domain;

/**
 * One post in the catalog's listing, as far as search could read it (AC-SRCH-38). A post search
 * cannot translate is never mistaken for a withdrawn post.
 */
public sealed interface CatalogEntry {

  /** Translated: goes through the normal index decision. */
  record Readable(PostToIndex post) implements CatalogEntry {}

  /** The post ID parses, but another field is malformed or missing: keep the entry, report it. */
  record Unreadable(PostId id, String reason) implements CatalogEntry {}

  /** The post ID itself cannot be read: no orphan can be identified safely. */
  record Unidentified(String reason) implements CatalogEntry {}
}
