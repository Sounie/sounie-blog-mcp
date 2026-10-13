package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx.Patchifier.MAX_PATCHES;
import static nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx.Patchifier.PATCH_VALUES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatchifierTest {

  /** A 48 x 48 image whose pixel at (x, y) is (x, y, 255): 3 x 3 patches. */
  private final VisionInput coordinates =
      Patchifier.patchify(
          new ResizedImage(
              new TargetSize(48, 48), TestImages.painted(48, 48, (x, y) -> new int[] {x, y, 255})));

  @Test
  @DisplayName("AC-SRCH-59: pixel values are 2,520 patches of 768 values")
  void pixel_values_fill_the_patch_budget() {
    assertThat(coordinates.pixelValues()).hasSize(MAX_PATCHES * PATCH_VALUES);
    assertThat(coordinates.positionIds()).hasSize(MAX_PATCHES * 2);
  }

  @Test
  @DisplayName(
      "AC-SRCH-59: patches go left to right then top to bottom, pixels by row, column, channel")
  void cuts_patches_in_row_major_order_and_rescales_each_sample() {
    float[] values = coordinates.pixelValues();
    for (int k = 0; k < 9; k++) {
      int column = k % 3;
      int row = k / 3;
      for (int py = 0; py < 16; py++) {
        for (int px = 0; px < 16; px++) {
          int at = k * PATCH_VALUES + (py * 16 + px) * 3;
          int x = column * 16 + px;
          int y = row * 16 + py;
          assertThat(values[at])
              .as("R of patch %d at (%d, %d)", k, px, py)
              .isCloseTo(x / 255f, within(1e-6f));
          assertThat(values[at + 1])
              .as("G of patch %d at (%d, %d)", k, px, py)
              .isCloseTo(y / 255f, within(1e-6f));
          assertThat(values[at + 2]).as("B of patch %d at (%d, %d)", k, px, py).isEqualTo(1.0f);
        }
      }
    }
  }

  @Test
  @DisplayName("AC-SRCH-59: rescaling maps 0 to exactly 0.0 and 255 to exactly 1.0, unnormalised")
  void rescales_without_normalising() {
    float[] values = coordinates.pixelValues();

    // Patch 0, pixel (0, 0) is (0, 0, 255).
    assertThat(values[0]).isEqualTo(0.0f);
    assertThat(values[1]).isEqualTo(0.0f);
    assertThat(values[2]).isEqualTo(1.0f);
  }

  @Test
  @DisplayName("AC-SRCH-59: each real patch's position id is its (column, row)")
  void positions_are_column_then_row() {
    long[] positions = coordinates.positionIds();

    for (int k = 0; k < 9; k++) {
      assertThat(new long[] {positions[2 * k], positions[2 * k + 1]})
          .as("position of patch %d", k)
          .containsExactly(k % 3, k / 3);
    }
  }

  @Test
  @DisplayName("AC-SRCH-59: padding patches are all 0.0 with position (-1, -1)")
  void pads_to_the_budget_with_zeros_at_position_minus_one() {
    float[] padding =
        Arrays.copyOfRange(coordinates.pixelValues(), 9 * PATCH_VALUES, MAX_PATCHES * PATCH_VALUES);
    long[] paddingPositions = Arrays.copyOfRange(coordinates.positionIds(), 9 * 2, MAX_PATCHES * 2);

    assertThat(IntStream.range(0, padding.length).filter(i -> padding[i] != 0.0f).count()).isZero();
    assertThat(Arrays.stream(paddingPositions).allMatch(id -> id == -1)).isTrue();
  }

  @Test
  @DisplayName("AC-SRCH-59: a 48 x 48 image is a 3 x 3 grid with 9 real patches and 1 token")
  void reports_the_patch_grid() {
    assertThat(coordinates.grid()).isEqualTo(new PatchGrid(3, 3));
    assertThat(coordinates.grid().realPatches()).isEqualTo(9);
    assertThat(coordinates.grid().imageTokens()).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-SRCH-59: a 912 x 672 image gives 2,394 real patches and 126 padding patches")
  void a_full_size_image_leaves_126_padding_patches() {
    VisionInput input =
        Patchifier.patchify(
            new ResizedImage(new TargetSize(912, 672), TestImages.uniform(912, 672, 9, 9, 9)));

    long[] positions = input.positionIds();
    long padding =
        IntStream.range(0, MAX_PATCHES)
            .filter(k -> positions[2 * k] == -1 && positions[2 * k + 1] == -1)
            .count();
    assertThat(input.grid().realPatches()).isEqualTo(2394);
    assertThat(padding).isEqualTo(126);
  }
}
