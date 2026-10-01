package nz.sounie.blogmcp.catalog.application;

import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingTest {

  private static final Instant T1 = Instant.parse("2026-09-20T01:00:00Z");

  private final Listing listing = new Listing();

  private static SourceEntry available(String id) {
    return new SourceEntry.Available(aSnapshot().sourcePostId(id).updatedAt(T1).build());
  }

  private static SourceEntry malformed(Optional<SourcePostId> id) {
    return new SourceEntry.Malformed(id, "bad entry", Optional.of(T1));
  }

  @Test
  @DisplayName("AC-CAT-21: a non-empty listing of identified entries allows withdrawals")
  void a_non_empty_identified_listing_allows_withdrawals() {
    listing.record(available("1"));
    listing.record(new SourceEntry.NotPublic(new SourcePostId("2"), Optional.of(T1)));

    assertThat(listing.withdrawalDecision()).isInstanceOf(WithdrawalDecision.Withdraw.class);
  }

  @Test
  @DisplayName("AC-CAT-22: an empty listing suppresses withdrawals")
  void an_empty_listing_suppresses_withdrawals() {
    assertThat(listing.withdrawalDecision())
        .isInstanceOfSatisfying(
            WithdrawalDecision.Suppress.class,
            suppress ->
                assertThat(suppress.warning())
                    .isEqualTo(SyncWarning.Kind.EMPTY_LISTING_WITHDRAWALS_SUPPRESSED));
  }

  @Test
  @DisplayName("AC-CAT-23: a malformed entry without a readable ID suppresses withdrawals")
  void an_unidentified_entry_suppresses_withdrawals() {
    listing.record(available("1"));
    listing.record(malformed(Optional.empty()));

    assertThat(listing.withdrawalDecision())
        .isInstanceOfSatisfying(
            WithdrawalDecision.Suppress.class,
            suppress -> {
              assertThat(suppress.warning())
                  .isEqualTo(SyncWarning.Kind.UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED);
              assertThat(suppress.detail()).isNotBlank();
            });
  }

  @Test
  @DisplayName("AC-CAT-23: a malformed entry with a readable ID still allows withdrawals")
  void an_identified_malformed_entry_still_allows_withdrawals() {
    listing.record(malformed(Optional.of(new SourcePostId("2"))));

    assertThat(listing.withdrawalDecision()).isInstanceOf(WithdrawalDecision.Withdraw.class);
  }

  @Test
  @DisplayName("AC-CAT-23: every entry with a readable ID counts as listed, malformed or not")
  void lists_every_identified_entry() {
    listing.record(available("1"));
    listing.record(new SourceEntry.NotPublic(new SourcePostId("2"), Optional.of(T1)));
    listing.record(malformed(Optional.of(new SourcePostId("3"))));
    listing.record(malformed(Optional.empty()));

    assertThat(listing.lists(new SourcePostId("1"))).isTrue();
    assertThat(listing.lists(new SourcePostId("2"))).isTrue();
    assertThat(listing.lists(new SourcePostId("3"))).isTrue();
    assertThat(listing.lists(new SourcePostId("4"))).isFalse();
  }
}
