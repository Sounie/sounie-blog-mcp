package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.domain.query.QueryText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Gemma2InputPreparationTest {

  @Test
  @DisplayName("AC-SRCH-46: Gemma2 applies its query prefix and passage template independently")
  void gemma2_prepares_query_and_passage_independently() {
    assertThat(Gemma2InputPreparation.gemma2Query(new QueryText("records in Java")))
        .isEqualTo("task: search result | query: records in Java");
    assertThat(Gemma2InputPreparation.gemma2Passage("Records in Java", "Canonical passage body"))
        .isEqualTo("title: Records in Java | text: Canonical passage body");
    assertThat(Gemma2InputPreparation.gemma2Passage("", "Canonical passage body"))
        .isEqualTo("title: none | text: Canonical passage body");
  }
}
