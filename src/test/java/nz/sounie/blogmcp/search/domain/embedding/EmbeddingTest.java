package nz.sounie.blogmcp.search.domain.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmbeddingTest {

  @ParameterizedTest
  @ValueSource(ints = {383, 385, 0})
  @DisplayName("AC-SRCH-9: a vector that is not 384-dimensional is rejected")
  void rejects_wrong_dimension(int dimension) {
    float[] values = new float[dimension];
    if (dimension > 0) {
      values[0] = 1f;
    }

    assertThatThrownBy(() -> new Embedding(384, values)).isInstanceOf(InvalidEmbedding.class);
  }

  @ParameterizedTest
  @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
  @DisplayName("AC-SRCH-9: a vector with a non-finite value is rejected")
  void rejects_non_finite_values(float bad) {
    float[] values = Vectors.axisValues(384, 0);
    values[200] = bad;

    assertThatThrownBy(() -> new Embedding(384, values)).isInstanceOf(InvalidEmbedding.class);
  }

  @Test
  @DisplayName("AC-SRCH-9: the zero vector is rejected")
  void rejects_zero_vector() {
    assertThatThrownBy(() -> new Embedding(384, new float[384]))
        .isInstanceOf(InvalidEmbedding.class);
  }

  @Test
  @DisplayName("AC-SRCH-9: a vector of length 3 is normalised to unit length")
  void normalises_to_unit_length() {
    float[] values = new float[384];
    values[0] = 1f;
    values[1] = 2f;
    values[2] = 2f;

    Embedding embedding = new Embedding(384, values);

    assertThat(embedding.values()[0]).isCloseTo(1f / 3, within(1e-6f));
    assertThat(embedding.values()[1]).isCloseTo(2f / 3, within(1e-6f));
    assertThat(norm(embedding)).isCloseTo(1.0, within(1e-6));
    assertThat(embedding.similarityTo(embedding).value()).isCloseTo(1.0, within(1e-6));
  }

  @Test
  @DisplayName("AC-SRCH-9: orthogonal embeddings have similarity 0")
  void orthogonal_similarity_is_zero() {
    assertThat(Vectors.axis(384, 0).similarityTo(Vectors.axis(384, 1)).value()).isCloseTo(0.0, within(1e-6));
  }

  @Test
  void opposite_embeddings_have_similarity_minus_one() {
    float[] negative = Vectors.axisValues(384, 5);
    negative[5] = -1f;

    assertThat(Vectors.axis(384, 5).similarityTo(new Embedding(384, negative)).value())
        .isCloseTo(-1.0, within(1e-6));
  }

  @Test
  void similarity_is_the_cosine() {
    assertThat(Vectors.query().similarityTo(Vectors.atSimilarity(384, 0.9)).value())
        .isCloseTo(0.9, within(1e-6));
  }

  @Test
  void does_not_share_the_callers_array() {
    float[] values = Vectors.axisValues(384, 0);
    Embedding embedding = new Embedding(384, values);

    values[0] = 0f;
    values[1] = 1f;

    assertThat(embedding.similarityTo(Vectors.axis(384, 0)).value()).isCloseTo(1.0, within(1e-6));
  }

  private static double norm(Embedding e) {
    double sum = 0;
    for (float v : e.values()) {
      sum += (double) v * v;
    }
    return Math.sqrt(sum);
  }

  @Test
  void embeddings_with_the_same_values_are_equal() {
    assertThat(Vectors.axis(384, 0)).isEqualTo(Vectors.axis(384, 0)).hasSameHashCodeAs(Vectors.axis(384, 0));
    assertThat(Vectors.axis(384, 0)).isNotEqualTo(Vectors.axis(384, 1)).isNotEqualTo("not an embedding");
    assertThat(Vectors.axis(384, 0).hashCode()).isNotEqualTo(Vectors.axis(384, 1).hashCode());
  }
}
