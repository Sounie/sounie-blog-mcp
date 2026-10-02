package nz.sounie.blogmcp.search.domain.index;

import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.chunkText;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IndexedPostTest {

  private static final ContentFingerprint STORED = fingerprint('a');
  private final PostToIndex post = aPost().id("sounie-wp:1").tags("Java").build();
  private final IndexedPost entry = indexed(post, STORED, Vectors.axis(1), Vectors.axis(2));

  @Nested
  class DecideFor {

    @Test
    void re_embeds_when_the_fingerprint_differs() {
      assertThat(entry.decideFor(post, fingerprint('b')))
          .isEqualTo(new IndexDecision.ReEmbed(post));
    }

    @Test
    void re_embeds_when_both_fingerprint_and_metadata_differ() {
      PostToIndex changed = aPost().id("sounie-wp:1").tags("Kotlin").build();

      assertThat(entry.decideFor(changed, fingerprint('b')))
          .isEqualTo(new IndexDecision.ReEmbed(changed));
    }

    @Test
    @DisplayName("AC-SRCH-14: same fingerprint, different metadata refreshes metadata")
    void refreshes_metadata_when_only_metadata_differs() {
      PostToIndex changed = aPost().id("sounie-wp:1").tags("Kotlin").build();

      assertThat(entry.decideFor(changed, STORED))
          .isEqualTo(new IndexDecision.RefreshMetadata(entry, changed.metadata()));
    }

    @Test
    @DisplayName("AC-SRCH-12: nothing differs, so the entry is kept")
    void keeps_when_nothing_differs() {
      assertThat(entry.decideFor(post, STORED)).isEqualTo(new IndexDecision.Keep(post.id()));
    }
  }

  @Test
  @DisplayName("AC-SRCH-14: withMetadata keeps chunks and fingerprint")
  void with_metadata_keeps_chunks_and_fingerprint() {
    PostMetadata newer = aPost().id("sounie-wp:1").tags("Kotlin").metadata();

    IndexedPost refreshed = entry.withMetadata(newer);

    assertThat(refreshed.metadata()).isEqualTo(newer);
    assertThat(refreshed.id()).isEqualTo(entry.id());
    assertThat(refreshed.fingerprint()).isEqualTo(STORED);
    assertThat(refreshed.chunks()).isEqualTo(entry.chunks());
    assertThat(entry.metadata()).isEqualTo(post.metadata());
  }

  @Nested
  class BestMatch {

    @Test
    @DisplayName("AC-SRCH-23: the score is the best chunk's similarity and the snippet its text")
    void picks_the_most_similar_chunk() {
      IndexedPost a =
          indexed(post, STORED, Vectors.atSimilarity(0.80, 1), Vectors.atSimilarity(0.90, 2));

      PostMatch match = a.bestMatch(Vectors.query()).orElseThrow();

      assertThat(match.score().value()).isCloseTo(0.90, within(1e-6));
      assertThat(match.snippet()).isEqualTo(chunkText(post.id(), 1));
      assertThat(match.postId()).isEqualTo(post.id());
      assertThat(match.metadata()).isEqualTo(post.metadata());
    }

    @Test
    @DisplayName("AC-SRCH-25: equal chunk scores pick the lower chunk index")
    void ties_go_to_the_lower_chunk_index() {
      IndexedPost a =
          indexed(
              post,
              STORED,
              Vectors.atSimilarity(0.5, 1),
              Vectors.atSimilarity(0.85, 2),
              Vectors.atSimilarity(0.85, 3));

      assertThat(a.bestMatch(Vectors.query()).orElseThrow().snippet())
          .isEqualTo(chunkText(post.id(), 1));
    }

    @Test
    @DisplayName("AC-SRCH-4: a post with no chunks never matches")
    void no_chunks_no_match() {
      IndexedPost empty = indexed(aPost().title("").body("").build(), STORED);

      assertThat(empty.bestMatch(Vectors.query())).isEmpty();
    }
  }

  @Nested
  class Invariants {

    @Test
    void metadata_site_must_be_the_post_id_site() {
      PostMetadata elsewhere = aPost().id("elegant:1").metadata();

      assertThatThrownBy(() -> IndexedPost.restore(post.id(), elsewhere, STORED, List.of()))
          .isInstanceOf(InvalidIndexedPost.class);
    }

    @Test
    void chunk_indexes_must_start_at_zero() {
      assertThatThrownBy(() -> restoreWithIndexes(1)).isInstanceOf(InvalidIndexedPost.class);
    }

    @Test
    void chunk_indexes_must_have_no_gaps() {
      assertThatThrownBy(() -> restoreWithIndexes(0, 2)).isInstanceOf(InvalidIndexedPost.class);
    }

    @Test
    void chunk_indexes_must_be_in_order() {
      assertThatThrownBy(() -> restoreWithIndexes(1, 0)).isInstanceOf(InvalidIndexedPost.class);
    }

    @Test
    void consecutive_indexes_from_zero_are_accepted() {
      assertThatCode(() -> restoreWithIndexes(0, 1, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-SRCH-4: a post may have zero chunks")
    void zero_chunks_are_accepted() {
      assertThat(restoreWithIndexes().chunks()).isEmpty();
    }

    private IndexedPost restoreWithIndexes(int... indexes) {
      List<IndexedChunk> chunks =
          java.util.Arrays.stream(indexes)
              .mapToObj(i -> new IndexedChunk(i, "c" + i, Vectors.axis(i + 1)))
              .toList();
      return IndexedPost.restore(post.id(), post.metadata(), STORED, chunks);
    }
  }
}
