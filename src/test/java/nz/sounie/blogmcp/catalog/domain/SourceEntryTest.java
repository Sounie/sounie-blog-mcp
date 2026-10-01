package nz.sounie.blogmcp.catalog.domain;

import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceEntryTest {

  private static final Instant T1 = Instant.parse("2026-09-20T01:20:47Z");
  private static final SourcePostId ID = new SourcePostId("27");

  @Test
  void an_available_entry_is_identified_and_dated_by_its_snapshot() {
    SourceEntry entry =
        new SourceEntry.Available(aSnapshot().sourcePostId("27").updatedAt(T1).build());

    assertThat(entry.readableSourcePostId()).contains(ID);
    assertThat(entry.updatedAt()).contains(T1);
  }

  @Test
  void a_not_public_entry_is_identified_by_its_source_post_id() {
    SourceEntry entry = new SourceEntry.NotPublic(ID, Optional.of(T1));

    assertThat(entry.readableSourcePostId()).contains(ID);
  }

  @Test
  void a_malformed_entry_is_identified_when_its_id_could_be_read() {
    SourceEntry entry = new SourceEntry.Malformed(Optional.of(ID), "bad date", Optional.empty());

    assertThat(entry.readableSourcePostId()).contains(ID);
  }

  @Test
  void a_malformed_entry_without_a_readable_id_is_unidentified() {
    SourceEntry entry = new SourceEntry.Malformed(Optional.empty(), "missing id", Optional.of(T1));

    assertThat(entry.readableSourcePostId()).isEmpty();
  }

  @Test
  void available_entry_notes_default_to_none() {
    assertThat(new SourceEntry.Available(aSnapshot().build()).notes()).isEmpty();
  }

  @Test
  void a_page_has_more_only_when_it_names_a_next_cursor() {
    assertThat(new SourcePage(List.of(), Optional.of(new PageCursor(2))).hasMore()).isTrue();
    assertThat(new SourcePage(List.of(), Optional.empty()).hasMore()).isFalse();
  }
}
