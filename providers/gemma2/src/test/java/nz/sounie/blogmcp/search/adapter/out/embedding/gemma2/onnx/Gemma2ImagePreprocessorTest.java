package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import java.util.List;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The composed preprocessing against the official processor's output for the six fixtures. Needs
 * only the fixtures and the reference JSON, not the model.
 */
class Gemma2ImagePreprocessorTest {

  /** Relative 1e-4, accepted by the owner on 2026-10-13. */
  private static final double PIXEL_SUM_TOLERANCE_PERCENT = 1e-4 * 100;

  static List<ImageReference.Image> fixtures() {
    return ImageReference.images();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  @DisplayName("AC-SRCH-61: preprocessing matches the reference grid, patches and input ids")
  void matches_the_reference_grid_patches_and_input_ids(ImageReference.Image reference) {
    ImagePixels pixels = reference.pixels();

    VisionInput input = Gemma2ImagePreprocessor.preprocess(pixels);

    assertThat(pixels.width()).isEqualTo(reference.width());
    assertThat(pixels.height()).isEqualTo(reference.height());
    assertThat(input.pixelValues()).hasSize(reference.patches() * Patchifier.PATCH_VALUES);
    assertThat(input.grid()).isEqualTo(new PatchGrid(reference.columns(), reference.rows()));
    assertThat(input.grid().realPatches()).isEqualTo(reference.realPatches());
    assertThat(realPositions(input)).isEqualTo(reference.realPatches());
    assertThat(input.grid().imageTokens()).isEqualTo(reference.imageTokens());
    assertThat(ImageInputIds.of(input.grid())).containsExactly(reference.inputIds());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  @DisplayName("AC-SRCH-61: the pixel-value sum is within a relative 1e-4 of the reference")
  void pixel_value_sum_matches_the_reference(ImageReference.Image reference) {
    VisionInput input = Gemma2ImagePreprocessor.preprocess(reference.pixels());

    float[] values = input.pixelValues();
    double sum = 0;
    for (float value : values) {
      sum += value;
    }
    assertThat(sum)
        .isCloseTo(reference.pixelValuesSum(), withinPercentage(PIXEL_SUM_TOLERANCE_PERCENT));
  }

  private static long realPositions(VisionInput input) {
    long[] positions = input.positionIds();
    return IntStream.range(0, positions.length / 2)
        .filter(k -> positions[2 * k] >= 0 && positions[2 * k + 1] >= 0)
        .count();
  }
}
