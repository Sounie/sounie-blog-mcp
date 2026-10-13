package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TargetSizeTest {

  @ParameterizedTest(name = "{0} x {1} -> {2} x {3}")
  @CsvSource({
    // 4:3 landscape, downscaled to fit the budget
    "1024, 768, 912, 672, 57, 42, 2394, 266",
    "1600, 1200, 912, 672, 57, 42, 2394, 266",
    // portrait
    "768, 1024, 672, 912, 42, 57, 2394, 266",
    // square: down, up, and up from a single pixel
    "1000, 1000, 768, 768, 48, 48, 2304, 256",
    "64, 64, 768, 768, 48, 48, 2304, 256",
    "1, 1, 768, 768, 48, 48, 2304, 256",
    // wide 40:1
    "4000, 100, 5040, 96, 315, 6, 1890, 210",
  })
  @DisplayName("AC-SRCH-56: the target size fits the patch budget and is a multiple of 48")
  void fits_the_patch_budget_in_multiples_of_48(
      int width,
      int height,
      int targetWidth,
      int targetHeight,
      int columns,
      int rows,
      int realPatches,
      int imageTokens) {
    TargetSize target = TargetSize.of(width, height);

    assertThat(target).isEqualTo(new TargetSize(targetWidth, targetHeight));
    assertThat(target.grid()).isEqualTo(new PatchGrid(columns, rows));
    assertThat(target.grid().realPatches()).isEqualTo(realPatches);
    assertThat(target.grid().imageTokens()).isEqualTo(imageTokens);
  }

  @ParameterizedTest(name = "{0} x {1}")
  @CsvSource({
    "1024, 768", "768, 1024", "1600, 1200", "1000, 1000", "64, 64", "1, 1", "4000, 100",
    "4096, 4096", "4032, 3024", "2048, 1365", "333, 777", "48, 48", "47, 49", "13440, 48",
    "10000, 10", "10, 10000", "1, 4096", "4096, 1",
  })
  @DisplayName("AC-SRCH-56: every target size is positive multiples of 48 within the budget")
  void every_target_is_positive_multiples_of_48_within_the_budget(int width, int height) {
    TargetSize target = TargetSize.of(width, height);

    assertThat(target.width()).isPositive();
    assertThat(target.width() % 48).as("width %d mod 48", target.width()).isZero();
    assertThat(target.height()).isPositive();
    assertThat(target.height() % 48).as("height %d mod 48", target.height()).isZero();
    assertThat(target.grid().realPatches()).isLessThanOrEqualTo(Patchifier.MAX_PATCHES);
  }

  @ParameterizedTest(name = "{0} x {1} -> {2} x {3}")
  @CsvSource({"10000, 10, 13440, 48", "10, 10000, 48, 13440"})
  @DisplayName("AC-SRCH-57: a side that floors to zero uses the reference processor's fallback")
  void a_side_flooring_to_zero_becomes_48_and_the_long_side_is_capped(
      int width, int height, int targetWidth, int targetHeight) {
    TargetSize target = TargetSize.of(width, height);

    assertThat(target).isEqualTo(new TargetSize(targetWidth, targetHeight));
    assertThat(target.grid().realPatches()).isEqualTo(2520);
    assertThat(target.grid().imageTokens()).isEqualTo(280);
  }
}
