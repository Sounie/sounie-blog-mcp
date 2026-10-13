package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.util.Arrays;

/**
 * Cuts a resized image into rescaled 16 × 16 patches with position ids, padded to the budget, as
 * the reference processor does ({@code convert_image_to_patches}, {@code pad_along_first_dim}):
 * patches row-major, values inside a patch ordered (row, column, channel), positions (column, row),
 * padding 0 with position −1.
 */
final class Patchifier {

  /** The patch budget: 280 image tokens × 9, and the vision encoder's fixed input length. */
  static final int MAX_PATCHES = 2520;

  /** Values in one flattened patch: 16 × 16 pixels × 3 channels. */
  static final int PATCH_VALUES = 768;

  private static final int SIZE = TargetSize.PATCH_SIZE;
  private static final int CHANNELS = 3;
  private static final long PADDING_POSITION = -1;

  /** Each 8-bit sample rescaled by 1/255 as the reference does: in double, then to float. */
  private static final float[] RESCALED = rescaleTable();

  private Patchifier() {}

  static VisionInput patchify(ResizedImage image) {
    PatchGrid grid = image.size().grid();
    return new VisionInput(pixelValues(image, grid), positionIds(grid), grid);
  }

  private static float[] rescaleTable() {
    float[] table = new float[256];
    for (int sample = 0; sample < table.length; sample++) {
      table[sample] = (float) (sample * (1.0 / 255));
    }
    return table;
  }

  private static float[] pixelValues(ResizedImage image, PatchGrid grid) {
    byte[] rgb = image.pixels().rgb();
    int width = image.size().width();
    float[] values = new float[MAX_PATCHES * PATCH_VALUES];
    int real = grid.realPatches() * PATCH_VALUES;
    for (int i = 0; i < real; i++) {
      values[i] = RESCALED[Byte.toUnsignedInt(rgb[sourceIndex(i, grid.columns(), width)])];
    }
    return values;
  }

  /** Where value {@code i} of the flattened patches comes from in the row-major RGB raster. */
  private static int sourceIndex(int i, int columns, int width) {
    int patch = i / PATCH_VALUES;
    int pixel = i % PATCH_VALUES / CHANNELS;
    int x = patch % columns * SIZE + pixel % SIZE;
    int y = patch / columns * SIZE + pixel / SIZE;
    return (y * width + x) * CHANNELS + i % CHANNELS;
  }

  private static long[] positionIds(PatchGrid grid) {
    long[] positions = new long[MAX_PATCHES * 2];
    Arrays.fill(positions, PADDING_POSITION);
    for (int patch = 0; patch < grid.realPatches(); patch++) {
      positions[2 * patch] = patch % grid.columns();
      positions[2 * patch + 1] = patch / grid.columns();
    }
    return positions;
  }
}
