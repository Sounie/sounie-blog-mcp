package nz.sounie.blogmcp.search.domain.index;

import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.chunkText;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.text.Words;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IndexedPostTest {

  private static final int DIMENSION = 384;

  private static final ContentFingerprint STORED = fingerprint('a');
  private final PostToIndex post = aPost().id("sounie-wp:1").tags("Java").build();
  private final IndexedPost entry =
      indexed(post, STORED, Vectors.axis(DIMENSION, 1), Vectors.axis(DIMENSION, 2));

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
          indexed(
              post,
              STORED,
              Vectors.atSimilarity(DIMENSION, 0.80, 1),
              Vectors.atSimilarity(DIMENSION, 0.90, 2));

      PostMatch match = a.bestMatch(Vectors.query()).orElseThrow();

      assertThat(match.score().value()).isCloseTo(0.90, within(1e-6));
      assertThat(match.snippet().text()).isEqualTo(chunkText(post.id(), 1));
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
              Vectors.atSimilarity(DIMENSION, 0.5, 1),
              Vectors.atSimilarity(DIMENSION, 0.85, 2),
              Vectors.atSimilarity(DIMENSION, 0.85, 3));

      assertThat(a.bestMatch(Vectors.query()).orElseThrow().snippet().text())
          .isEqualTo(chunkText(post.id(), 1));
    }

    @Test
    @DisplayName("AC-SRCH-39: the match's snippet is the best chunk's snippet, not the whole chunk")
    void the_snippet_is_cut_from_the_best_chunk() {
      String longChunk = Words.numbered(300);
      IndexedPost a =
          IndexedPost.restore(
              post.id(),
              post.metadata(),
              STORED,
              List.of(new IndexedChunk(0, longChunk, Vectors.atSimilarity(DIMENSION, 0.9))));

      PostMatch match = a.bestMatch(Vectors.query()).orElseThrow();

      assertThat(match.snippet()).isEqualTo(Snippet.of(longChunk));
      assertThat(match.snippet().truncated()).isTrue();
    }

    @Test
    @DisplayName("AC-SRCH-4: a post with no chunks never matches")
    void no_chunks_no_match() {
      IndexedPost empty = indexed(aPost().title("").body("").build(), STORED);

      assertThat(empty.bestMatch(Vectors.query())).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-73: an image as the best entry gives the score, snippet and URL")
    void an_image_can_be_the_best_entry() {
      IndexedPost a =
          withImages(
              List.of(
                  new IndexedChunk(0, "c0", Vectors.atSimilarity(DIMENSION, 0.70, 1)),
                  new IndexedChunk(1, "c1", Vectors.atSimilarity(DIMENSION, 0.60, 2))),
              new IndexedImage(0, SWAN, "Black swan", Vectors.atSimilarity(DIMENSION, 0.78, 3)));

      PostMatch match = a.bestMatch(Vectors.query()).orElseThrow();

      assertThat(match.score().value()).isCloseTo(0.78, within(1e-6));
      assertThat(match.snippet()).isEqualTo(new Snippet("Black swan", false));
      assertThat(match.matchedImage()).contains(SWAN);
    }

    @Test
    @DisplayName("AC-SRCH-73: a chunk as the best entry has no matched image")
    void a_chunk_match_has_no_matched_image() {
      IndexedPost a =
          withImages(
              List.of(new IndexedChunk(0, "c0", Vectors.atSimilarity(DIMENSION, 0.75, 1))),
              new IndexedImage(0, SWAN, "Black swan", Vectors.atSimilarity(DIMENSION, 0.5, 3)));

      PostMatch match = a.bestMatch(Vectors.query()).orElseThrow();

      assertThat(match.snippet().text()).isEqualTo("c0");
      assertThat(match.matchedImage()).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-73: a post with zero chunks matches through its image")
    void a_post_without_chunks_matches_through_an_image() {
      IndexedPost imageOnly =
          withImages(
              List.of(),
              new IndexedImage(0, SWAN, "Black swan", Vectors.atSimilarity(DIMENSION, 0.4, 3)));

      assertThat(imageOnly.bestMatch(Vectors.query()))
          .hasValueSatisfying(match -> assertThat(match.matchedImage()).contains(SWAN));
    }

    @Test
    @DisplayName("AC-SRCH-67: a long image description gives a 60-word snippet")
    void a_long_description_is_cut_like_a_chunk() {
      String seventyWords = Words.numbered(70);
      IndexedPost a =
          withImages(
              List.of(),
              new IndexedImage(0, SWAN, seventyWords, Vectors.atSimilarity(DIMENSION, 0.9, 3)));

      assertThat(a.bestMatch(Vectors.query()).orElseThrow().snippet())
          .isEqualTo(new Snippet(Words.numbered(60) + " …", true));
    }
  }

  @Test
  @DisplayName("Invariant 7: withMetadata keeps the images")
  void with_metadata_keeps_images() {
    IndexedPost withImage =
        withImages(List.of(), new IndexedImage(0, SWAN, "Swan", Vectors.axis(DIMENSION, 5)));

    IndexedPost refreshed =
        withImage.withMetadata(aPost().id("sounie-wp:1").tags("Kotlin").metadata());

    assertThat(refreshed.images()).isEqualTo(withImage.images());
  }

  private static final URI SWAN = URI.create("https://x.example/swan.jpg");

  private IndexedPost withImages(List<IndexedChunk> chunks, IndexedImage... images) {
    return IndexedPost.restore(post.id(), post.metadata(), STORED, chunks, List.of(images));
  }

  private IndexedPost withImageIndexes(int... indexes) {
    List<IndexedImage> images =
        java.util.Arrays.stream(indexes)
            .mapToObj(
                i ->
                    new IndexedImage(
                        i,
                        URI.create("https://x.example/" + i + ".jpg"),
                        "image " + i,
                        Vectors.axis(DIMENSION, 10 + Math.abs(i))))
            .toList();
    return IndexedPost.restore(post.id(), post.metadata(), STORED, List.of(), images);
  }

  @Nested
  class ImageInvariants {

    @ParameterizedTest
    @MethodSource("nz.sounie.blogmcp.search.domain.index.IndexedPostTest#invalidImageIndexes")
    @DisplayName("AC-SRCH-69: image indexes must be distinct, non-negative and ascending")
    void rejects_invalid_image_indexes(int[] indexes) {
      assertThatThrownBy(() -> withImageIndexes(indexes)).isInstanceOf(InvalidIndexedPost.class);
    }

    @Test
    @DisplayName("AC-SRCH-69: gaps in image indexes are accepted (skipped images)")
    void accepts_gaps_in_image_indexes() {
      assertThat(withImageIndexes(0, 2).images())
          .extracting(IndexedImage::index)
          .containsExactly(0, 2);
    }

    @Test
    void no_images_are_accepted() {
      assertThat(withImageIndexes().images()).isEmpty();
    }
  }

  static Stream<int[]> invalidImageIndexes() {
    return Stream.of(new int[] {1, 0}, new int[] {0, 0}, new int[] {-1});
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
              .mapToObj(i -> new IndexedChunk(i, "c" + i, Vectors.axis(DIMENSION, i + 1)))
              .toList();
      return IndexedPost.restore(post.id(), post.metadata(), STORED, chunks);
    }
  }
}
