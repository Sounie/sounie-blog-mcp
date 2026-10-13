package nz.sounie.blogmcp.search.domain.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EntryHit#BEST_FIRST}: similarity descending, then chunks before images, then the lower
 * index. Hits are made by each entry's own {@code hitFor}, as {@code bestMatch} makes them.
 */
class EntryHitTest {

  private static final int DIMENSION = 384;
  private static final Embedding QUERY = Vectors.query();

  private static EntryHit chunk(int index, double similarity) {
    return new IndexedChunk(index, "chunk " + index, at(similarity, index)).hitFor(QUERY);
  }

  private static EntryHit image(int index, double similarity) {
    return new IndexedImage(
            index,
            URI.create("https://x.example/" + index + ".jpg"),
            "image " + index,
            at(similarity, 100 + index))
        .hitFor(QUERY);
  }

  private static Embedding at(double similarity, int otherAxis) {
    return Vectors.atSimilarity(DIMENSION, similarity, 1 + otherAxis % (DIMENSION - 1));
  }

  private static EntryHit best(EntryHit... hits) {
    return Stream.of(hits).min(EntryHit.BEST_FIRST).orElseThrow();
  }

  @Test
  @DisplayName("AC-SRCH-73: a more similar image beats a chunk")
  void a_more_similar_image_beats_a_chunk() {
    assertThat(best(chunk(0, 0.70), image(0, 0.78), chunk(1, 0.60))).isEqualTo(image(0, 0.78));
  }

  @Test
  void a_more_similar_chunk_beats_an_image() {
    assertThat(best(image(0, 0.70), chunk(0, 0.75))).isEqualTo(chunk(0, 0.75));
  }

  @Test
  @DisplayName("AC-SRCH-73: with equal scores, a chunk beats an image")
  void equal_scores_go_to_the_chunk() {
    assertThat(best(image(0, 0.8), chunk(3, 0.8))).isEqualTo(chunk(3, 0.8));
  }

  @Test
  @DisplayName("AC-SRCH-73: with equal scores, the lower image index wins")
  void equal_image_scores_go_to_the_lower_index() {
    assertThat(best(image(2, 0.8), image(0, 0.8))).isEqualTo(image(0, 0.8));
  }

  @Test
  @DisplayName("AC-SRCH-25: with equal scores, the lower chunk index wins")
  void equal_chunk_scores_go_to_the_lower_index() {
    assertThat(best(chunk(2, 0.8), chunk(1, 0.8))).isEqualTo(chunk(1, 0.8));
  }
}
