package nz.sounie.blogmcp.search.adapter.out.embedding.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.domain.query.QueryText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingInputPreparationTest {

  @Test
  @DisplayName(
      "AC-SRCH-46: ONNX applies its query instruction and leaves passage content unchanged")
  void onnx_prepares_queries_only() {
    assertThat(EmbeddingInputPreparation.onnxQuery(new QueryText("records in Java")))
        .isEqualTo("Represent this sentence for searching relevant passages: records in Java");
    assertThat(EmbeddingInputPreparation.onnxPassage("canonical title\ncanonical body"))
        .isEqualTo("canonical title\ncanonical body");
  }
}
