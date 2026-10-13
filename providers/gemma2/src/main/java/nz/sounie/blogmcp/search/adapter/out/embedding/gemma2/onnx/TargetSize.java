package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/**
 * The size an image is resized to before patchifying (search.md 2.1). {@link #of} always gives
 * positive multiples of 48 within the patch budget; the constructor itself accepts any size, so the
 * resize can be tested on small targets.
 */
record TargetSize(int width, int height) {

  static final int PATCH_SIZE = 16;

  /** One image token's side in pixels: 16 px × the 3 × 3 pooling. */
  private static final int SIDE_MULTIPLE = PATCH_SIZE * PatchGrid.POOLING;

  private static final double BUDGET_PIXELS = Patchifier.MAX_PATCHES * PATCH_SIZE * PATCH_SIZE;

  /** The longest side the fallback allows: (2,520 ÷ 9) × 48 = 13,440. */
  private static final int MAX_SIDE =
      Patchifier.MAX_PATCHES / (PatchGrid.POOLING * PatchGrid.POOLING) * SIDE_MULTIPLE;

  /**
   * The target size for an image of this size, as the reference processor computes it ({@code
   * get_aspect_ratio_preserving_size}): both sides scaled by one factor to fit the budget, then
   * floored to a multiple of 48. A side that floors to 0 becomes 48, and the other side is then the
   * aspect ratio, floored, times 48, capped at 13,440 (AC-SRCH-56, 57). Both sides cannot floor to
   * 0, because the scaled area is the whole budget.
   */
  static TargetSize of(int width, int height) {
    double factor = Math.sqrt(BUDGET_PIXELS / ((double) width * height));
    int scaledWidth = floorToSideMultiple(factor * width);
    int scaledHeight = floorToSideMultiple(factor * height);
    if (scaledWidth == 0) {
      return new TargetSize(SIDE_MULTIPLE, fallbackLongSide(height, width));
    }
    if (scaledHeight == 0) {
      return new TargetSize(fallbackLongSide(width, height), SIDE_MULTIPLE);
    }
    return new TargetSize(scaledWidth, scaledHeight);
  }

  private static int floorToSideMultiple(double length) {
    return (int) Math.floor(length / SIDE_MULTIPLE) * SIDE_MULTIPLE;
  }

  private static int fallbackLongSide(int longSide, int shortSide) {
    return Math.min((int) Math.floor((double) longSide / shortSide) * SIDE_MULTIPLE, MAX_SIDE);
  }

  /** This size counted in 16-pixel patches. */
  PatchGrid grid() {
    return new PatchGrid(width / PATCH_SIZE, height / PATCH_SIZE);
  }
}
