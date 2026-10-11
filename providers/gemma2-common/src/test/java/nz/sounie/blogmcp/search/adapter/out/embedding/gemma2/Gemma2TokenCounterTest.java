package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The real pinned tokenizer through DJL; no test doubles. */
class Gemma2TokenCounterTest {

  private final Gemma2TokenCounter tokens = new Gemma2TokenCounter();

  @Test
  void model_input_wraps_the_text_in_bos_and_eos() {
    assertThat(tokens.modelInputIds("hello world")).containsExactly(2, 23391, 1902, 1);
  }

  @Test
  void counting_leaves_out_the_special_tokens() {
    assertThat(tokens.countTokens("hello world")).isEqualTo(2);
  }

  @Test
  @DisplayName("AC-SRCH-48: a missing pinned tokenizer fails Gemma2 startup, naming the resource")
  void a_missing_tokenizer_fails_naming_it() {
    String missing = "/embeddinggemma-2/missing-tokenizer.json";

    assertThatThrownBy(() -> new Gemma2TokenCounter(missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(missing);
  }
}
