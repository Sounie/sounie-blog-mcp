package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Not tagged {@code model}: neither test may load either model. */
class Gemma2OnnxImageCapabilityTest {

  @Test
  @DisplayName("AC-SRCH-55: the in-process Gemma2 provider has an image embedder")
  void has_an_image_embedder() {
    assertThat(new Gemma2OnnxEmbedder().imageEmbedder()).isPresent();
  }

  @Test
  @DisplayName("AC-SRCH-65: an image too large to resize is reported as EmbedderUnavailable")
  void reports_an_image_too_large_to_resize_as_embedder_unavailable() {
    int height = (int) ImagePixels.MAX_PIXELS;
    ImagePixels tallAndNarrow = new ImagePixels(1, height, new byte[height * 3]);
    ImageEmbedder embedder = new Gemma2OnnxEmbedder().imageEmbedder().orElseThrow();

    assertThatThrownBy(() -> embedder.embedImage(tallAndNarrow))
        .isInstanceOf(EmbedderUnavailable.class)
        .hasCauseInstanceOf(ImageTooLargeToResize.class);
  }
}
