package nz.sounie.blogmcp.search.domain.index;

import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.post.MalformedCatalogPost;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-SRCH-37: Completeness alone decides indexability, reached through IndexDecision.forPost. */
class CompletenessTest {

  private static final int DIMENSION = 384;
  private static final ContentFingerprint CURRENT = fingerprint('a');

  /** The existing entry, relative to the post to index. */
  enum Existing {
    ABSENT,
    IDENTICAL,
    METADATA_DIFFERS,
    FINGERPRINT_DIFFERS;

    Optional<IndexedPost> entryFor(PostToIndex post) {
      PostToIndex stored = this == METADATA_DIFFERS ? withOtherTags(post) : post;
      ContentFingerprint fp = this == FINGERPRINT_DIFFERS ? fingerprint('b') : CURRENT;
      return this == ABSENT ? Optional.empty() : Optional.of(indexed(stored, fp, Vectors.axis(DIMENSION, 1)));
    }

    private static PostToIndex withOtherTags(PostToIndex post) {
      return new PostToIndex(
          post.id(),
          post.completeness(),
          new PostMetadata(
              post.metadata().siteId(),
              post.metadata().canonicalUrl(),
              post.metadata().title(),
              java.util.Set.of("Old tag"),
              post.metadata().publishedAt(),
              post.metadata().updatedAt()),
          post.title(),
          post.body());
    }
  }

  @ParameterizedTest
  @EnumSource(Existing.class)
  @DisplayName("AC-SRCH-37: a SUMMARY post is always excluded")
  void summary_is_always_excluded(Existing existing) {
    PostToIndex post = aPost().id("elegant:5").summary().build();

    IndexDecision decision = IndexDecision.forPost(existing.entryFor(post), post, CURRENT);

    assertThat(decision).isEqualTo(new IndexDecision.Exclude(post.id()));
  }

  @Test
  @DisplayName("AC-SRCH-37: a FULL post with no entry is added")
  void full_and_absent_is_add() {
    PostToIndex post = aPost().build();

    assertThat(IndexDecision.forPost(Existing.ABSENT.entryFor(post), post, CURRENT))
        .isEqualTo(new IndexDecision.Add(post));
  }

  @Test
  @DisplayName("AC-SRCH-37: a FULL post identical to its entry is kept")
  void full_and_identical_is_keep() {
    PostToIndex post = aPost().build();

    assertThat(IndexDecision.forPost(Existing.IDENTICAL.entryFor(post), post, CURRENT))
        .isEqualTo(new IndexDecision.Keep(post.id()));
  }

  @Test
  @DisplayName("AC-SRCH-37: a FULL post whose metadata differs is refreshed")
  void full_and_metadata_differs_is_refresh() {
    PostToIndex post = aPost().tags("New tag").build();

    assertThat(IndexDecision.forPost(Existing.METADATA_DIFFERS.entryFor(post), post, CURRENT))
        .isInstanceOfSatisfying(
            IndexDecision.RefreshMetadata.class,
            refresh -> assertThat(refresh.metadata()).isEqualTo(post.metadata()));
  }

  @Test
  @DisplayName("AC-SRCH-37: a FULL post whose fingerprint differs is re-embedded")
  void full_and_fingerprint_differs_is_re_embed() {
    PostToIndex post = aPost().build();

    assertThat(IndexDecision.forPost(Existing.FINGERPRINT_DIFFERS.entryFor(post), post, CURRENT))
        .isEqualTo(new IndexDecision.ReEmbed(post));
  }

  @ParameterizedTest
  @EnumSource(Existing.class)
  @DisplayName("AC-SRCH-37: forPost gives exactly what the post's completeness decides")
  void for_post_delegates_to_completeness(Existing existing) {
    PostToIndex post = aPost().build();
    Optional<IndexedPost> entry = existing.entryFor(post);

    assertThat(IndexDecision.forPost(entry, post, CURRENT))
        .isEqualTo(Completeness.FULL.decide(entry, post, CURRENT));
  }

  @Test
  void parses_full_and_summary() {
    assertThat(Completeness.parse("FULL")).isEqualTo(Completeness.FULL);
    assertThat(Completeness.parse("SUMMARY")).isEqualTo(Completeness.SUMMARY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PARTIAL", "full", ""})
  void rejects_unknown_completeness(String text) {
    assertThatThrownBy(() -> Completeness.parse(text)).isInstanceOf(MalformedCatalogPost.class);
  }
}
