package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryPassageTest {

  @Test
  @DisplayName("AC-SRCH-32: a query passage is the instruction followed by the query text")
  void prefixes_the_query_instruction() {
    assertThat(QueryPassage.of(new QueryText(" records in java ")).text())
        .isEqualTo("Represent this sentence for searching relevant passages: records in java");
  }
}
