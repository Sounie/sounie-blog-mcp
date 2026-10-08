package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import nz.sounie.blogmcp.search.adapter.out.Gemma2Embedder;
import nz.sounie.blogmcp.search.adapter.out.OnnxEmbedder;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingProviderAdaptersTest {

  private static final PassageTokenBudget GEMMA_BUDGET =
      new PassageTokenBudget(80, 12, 8, 100, 128);

  @Test
  @DisplayName("AC-SRCH-40: absent configuration selects ONNX and includes it in the recipe")
  void absent_selection_wires_onnx_and_its_model_identity_into_the_recipe() {
    Adapters adapters =
        Adapters.production(EmbeddingProviderSelection.fromEnvironment(java.util.Map.of()));

    assertThat(adapters.passageEmbedder()).isInstanceOf(OnnxEmbedder.class);
    assertThat(adapters.queryEmbedder()).isSameAs(adapters.passageEmbedder());
    assertThat(adapters.passageEmbedder().modelId()).isEqualTo(OnnxEmbedder.MODEL_ID);
    assertThat(recipeFor(adapters)).startsWith(OnnxEmbedder.MODEL_ID + "/");
  }

  @Test
  @DisplayName("AC-SRCH-41, 44: explicit ONNX uses one instance for both embedding ports")
  void explicit_onnx_shares_its_selected_instance_between_both_ports() {
    Adapters adapters =
        Adapters.production(
            EmbeddingProviderSelection.fromEnvironment(
                java.util.Map.of("BLOG_MCP_EMBEDDING_PROVIDER", "onnx")));

    assertThat(adapters.passageEmbedder()).isInstanceOf(OnnxEmbedder.class);
    assertThat(adapters.queryEmbedder()).isSameAs(adapters.passageEmbedder());
  }

  @Test
  @DisplayName("AC-SRCH-42, 44: Gemma2 uses its pinned profile and one shared adapter instance")
  void gemma2_shares_one_adapter_configured_with_its_explicit_profile() {
    Gemma2EmbeddingProfile profile =
        new Gemma2EmbeddingProfile(
            "fixture-revision-1",
            "fixture-revision-1",
            Path.of("model-assets/fixture-revision-1/tokenizer"),
            GEMMA_BUDGET);
    Adapters adapters =
        Adapters.production(
            EmbeddingProviderSelection.fromEnvironment(
                java.util.Map.of("BLOG_MCP_EMBEDDING_PROVIDER", "gemma2")),
            profile);

    assertThat(adapters.passageEmbedder()).isInstanceOf(Gemma2Embedder.class);
    assertThat(adapters.queryEmbedder()).isSameAs(adapters.passageEmbedder());
    assertThat(adapters.passageEmbedder().modelId())
        .contains("google-embeddinggemma-2", "fixture-revision-1");
    assertThat(recipeFor(adapters)).contains("fixture-revision-1");
  }

  private static String recipeFor(Adapters adapters) {
    return new nz.sounie.blogmcp.search.domain.index.PostIndexer(
            nz.sounie.blogmcp.search.domain.text.ChunkingPolicy.standard(),
            nz.sounie.blogmcp.search.domain.text.PassageComposition.standard(),
            adapters.tokenCounter(),
            adapters.passageEmbedder())
        .recipe()
        .id();
  }
}
