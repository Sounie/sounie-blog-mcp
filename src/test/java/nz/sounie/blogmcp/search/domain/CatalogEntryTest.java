package nz.sounie.blogmcp.search.domain;

import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-SRCH-38: which catalog entries block orphan removal, and why. */
class CatalogEntryTest {

  @Test
  @DisplayName("AC-SRCH-38: a readable entry never blocks orphan removal")
  void readable_entry_has_no_blockers() {
    assertThat(new CatalogEntry.Readable(aPost().build()).orphanRemovalBlockers()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-38: an unreadable entry never blocks orphan removal (its ID is known)")
  void unreadable_entry_has_no_blockers() {
    assertThat(
            new CatalogEntry.Unreadable(PostId.parse("sounie-wp:4"), "completeness is PARTIAL")
                .orphanRemovalBlockers())
        .isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-38: an unidentified entry blocks orphan removal with its reason")
  void unidentified_entry_blocks_with_its_reason() {
    assertThat(
            new CatalogEntry.Unidentified("post ID 'not-an-id' is not <siteId>:<sourcePostId>")
                .orphanRemovalBlockers())
        .containsExactly("post ID 'not-an-id' is not <siteId>:<sourcePostId>");
  }
}
