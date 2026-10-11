package nz.sounie.blogmcp.catalog.application;

import java.util.HashSet;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.post.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;

/** What one sync saw listed by the source, and what that allows a reconcile to withdraw. */
final class Listing {

  private final Set<SourcePostId> listed = new HashSet<>();
  private int entries;
  private boolean unidentifiedEntrySeen;

  void record(SourceEntry entry) {
    entries++;
    entry.readableSourcePostId().ifPresentOrElse(listed::add, () -> unidentifiedEntrySeen = true);
  }

  boolean lists(SourcePostId sourcePostId) {
    return listed.contains(sourcePostId);
  }

  /**
   * Withdrawing is safe only if the listing was non-empty and every entry could be identified;
   * otherwise a post that is still listed could look missing.
   */
  WithdrawalDecision withdrawalDecision() {
    if (entries == 0) {
      return new WithdrawalDecision.Suppress(
          SyncWarning.Kind.EMPTY_LISTING_WITHDRAWALS_SUPPRESSED,
          "the reconcile listed no entries, so no post was withdrawn");
    }
    if (unidentifiedEntrySeen) {
      return new WithdrawalDecision.Suppress(
          SyncWarning.Kind.UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED,
          "a malformed entry had no readable source post ID, so no post was withdrawn");
    }
    return new WithdrawalDecision.Withdraw();
  }
}
