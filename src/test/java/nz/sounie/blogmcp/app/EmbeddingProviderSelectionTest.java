package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EmbeddingProviderSelectionTest {

  private static final String PROVIDER_VARIABLE = "BLOG_MCP_EMBEDDING_PROVIDER";

  @Test
  @DisplayName("AC-SRCH-40: an absent provider selection defaults to ONNX BGE")
  void absent_provider_selects_onnx() {
    assertThat(EmbeddingProviderSelection.fromEnvironment(Map.of()).provider())
        .isEqualTo(EmbeddingProviderSelection.Provider.ONNX);
  }

  @Test
  @DisplayName("AC-SRCH-41: an explicit onnx selection selects ONNX BGE")
  void explicit_onnx_selects_onnx() {
    assertThat(
            EmbeddingProviderSelection.fromEnvironment(Map.of(PROVIDER_VARIABLE, "onnx"))
                .provider())
        .isEqualTo(EmbeddingProviderSelection.Provider.ONNX);
  }

  @Test
  @DisplayName("AC-SRCH-42: an explicit gemma2 selection selects the Gemma2 provider")
  void explicit_gemma2_selects_gemma2() {
    assertThat(
            EmbeddingProviderSelection.fromEnvironment(Map.of(PROVIDER_VARIABLE, "gemma2"))
                .provider())
        .isEqualTo(EmbeddingProviderSelection.Provider.GEMMA2);
  }

  static Stream<String> invalidSelections() {
    return Stream.of("", " \t ", "ONNX", "Gemma2", "other");
  }

  @ParameterizedTest
  @MethodSource("invalidSelections")
  @DisplayName("AC-SRCH-43: blank or unsupported provider selections are rejected")
  void rejects_blank_or_unknown_provider(String configuredProvider) {
    assertThatThrownBy(
            () ->
                EmbeddingProviderSelection.fromEnvironment(
                    Map.of(PROVIDER_VARIABLE, configuredProvider)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("AC-SRCH-43: surrounding whitespace is trimmed before matching")
  void trims_provider_selection_before_matching() {
    assertThat(
            EmbeddingProviderSelection.fromEnvironment(Map.of(PROVIDER_VARIABLE, "  gemma2\t"))
                .provider())
        .isEqualTo(EmbeddingProviderSelection.Provider.GEMMA2);
  }
}
