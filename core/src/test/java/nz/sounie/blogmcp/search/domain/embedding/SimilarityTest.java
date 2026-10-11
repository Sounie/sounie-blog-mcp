package nz.sounie.blogmcp.search.domain.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimilarityTest {

  @Test
  void higher_similarity_compares_greater() {
    assertThat(new Similarity(0.9)).isGreaterThan(new Similarity(0.8));
    assertThat(new Similarity(0.5)).isEqualByComparingTo(new Similarity(0.5));
  }
}
