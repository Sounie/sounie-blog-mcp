package nz.sounie.blogmcp.search.adapter.out.embedding.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Not tagged {@code model}: asking for the capability must not load the BGE model. */
class OnnxImageCapabilityTest {

  @Test
  @DisplayName("AC-SRCH-55: the BGE provider is text-only")
  void has_no_image_embedder() {
    assertThat(new OnnxEmbedder().imageEmbedder()).isEmpty();
  }
}
