package nz.sounie.blogmcp.catalog.application;

import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;

/** Something worth noting in a sync report that is not a skipped entry. */
public record SyncWarning(Kind kind, Optional<SourcePostId> sourcePostId, String detail) {

  /** What the warning is about. */
  public enum Kind {
    /** A complete reconcile listed zero entries, so nothing was withdrawn. */
    EMPTY_LISTING_WITHDRAWALS_SUPPRESSED,
    /** A malformed entry had no readable source post ID, so nothing was withdrawn. */
    UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED,
    /** A note from the blog source, e.g. an unresolvable tag reference that was dropped. */
    SOURCE_NOTE
  }
}
