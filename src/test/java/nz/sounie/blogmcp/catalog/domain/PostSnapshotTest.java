package nz.sounie.blogmcp.catalog.domain;

import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostSnapshotTest {

  private static final Instant PUBLISHED = Instant.parse("2026-09-20T01:20:47Z");

  @Test
  void updated_at_earlier_than_published_at_is_normalised_to_published_at() {
    PostSnapshot snapshot =
        aSnapshot().publishedAt(PUBLISHED).updatedAt(PUBLISHED.minusSeconds(60)).build();

    assertThat(snapshot.updatedAt()).isEqualTo(PUBLISHED);
  }

  @Test
  void updated_at_later_than_published_at_is_kept() {
    Instant later = PUBLISHED.plusSeconds(60);

    PostSnapshot snapshot = aSnapshot().publishedAt(PUBLISHED).updatedAt(later).build();

    assertThat(snapshot.updatedAt()).isEqualTo(later);
  }

  @Test
  void tags_cannot_be_modified_through_the_snapshot() {
    PostSnapshot snapshot = aSnapshot().tags("DDD").build();

    assertThatThrownBy(() -> snapshot.tags().add(new Tag("Java")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void title_and_body_may_be_empty() {
    PostSnapshot snapshot = aSnapshot().title("").body("").build();

    assertThat(snapshot.title().value()).isEmpty();
    assertThat(snapshot.body().text()).isEmpty();
  }

  @Test
  void title_and_body_are_never_null() {
    assertThatThrownBy(() -> new Title(null))
        .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    assertThatThrownBy(() -> new Body(null))
        .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
  }
}
