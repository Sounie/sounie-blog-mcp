package nz.sounie.blogmcp.search.adapter.out;

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

  @Test
  @DisplayName("AC-SRCH-46: Gemma2 applies its query prefix and passage template independently")
  void gemma2_prepares_query_and_passage_independently() {
    assertThat(EmbeddingInputPreparation.gemma2Query(new QueryText("records in Java")))
        .isEqualTo("task: search result | query: records in Java");
    assertThat(EmbeddingInputPreparation.gemma2Passage("Records in Java", "Canonical passage body"))
        .isEqualTo("title: Records in Java | text: Canonical passage body");
    assertThat(EmbeddingInputPreparation.gemma2Passage("", "Canonical passage body"))
        .isEqualTo("title: none | text: Canonical passage body");
  }
}
