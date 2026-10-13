package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The row-count check on the vision output, without the model (search.md 3.3). */
class Gemma2VisionEncoderTest {

  private static final PatchGrid GRID_266_TOKENS = new PatchGrid(57, 42);

  @Test
  @DisplayName("AC-SRCH-65: features with one row per image token are passed on")
  void accepts_one_row_per_image_token() {
    float[][] features = new float[266][512];

    assertThat(Gemma2VisionEncoder.checkedFeatures(features, GRID_266_TOKENS)).isSameAs(features);
  }

  @Test
  @DisplayName("AC-SRCH-65: a vision output whose row count differs from the image tokens fails")
  void rejects_a_row_count_other_than_the_image_tokens() {
    assertThatThrownBy(
            () -> Gemma2VisionEncoder.checkedFeatures(new float[265][512], GRID_266_TOKENS))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> Gemma2VisionEncoder.checkedFeatures(new float[267][512], GRID_266_TOKENS))
        .isInstanceOf(IllegalStateException.class);
  }
}
