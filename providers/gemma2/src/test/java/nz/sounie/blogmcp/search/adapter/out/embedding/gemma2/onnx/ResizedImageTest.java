package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The size check the patchifier relies on, so it never re-checks it (search.md 2.1). */
class ResizedImageTest {

  @Test
  @DisplayName("search.md 2.1: a resized image holds pixels of exactly its target size")
  void accepts_pixels_of_the_target_size() {
    ResizedImage image =
        new ResizedImage(new TargetSize(48, 96), TestImages.uniform(48, 96, 0, 0, 0));

    assertThat(image.pixels().height()).isEqualTo(96);
  }

  @Test
  @DisplayName("search.md 2.1: pixels of another size are not a resized image")
  void rejects_pixels_of_another_size() {
    assertThatThrownBy(
            () -> new ResizedImage(new TargetSize(48, 96), TestImages.uniform(96, 48, 0, 0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
