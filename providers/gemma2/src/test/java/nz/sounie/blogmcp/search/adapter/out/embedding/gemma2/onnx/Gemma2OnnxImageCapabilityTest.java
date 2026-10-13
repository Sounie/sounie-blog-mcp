package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Not tagged {@code model}: asking for the capability must not load either model. */
class Gemma2OnnxImageCapabilityTest {

  @Test
  @DisplayName("AC-SRCH-55: the in-process Gemma2 provider has an image embedder")
  void has_an_image_embedder() {
    assertThat(new Gemma2OnnxEmbedder().imageEmbedder()).isPresent();
  }
}
