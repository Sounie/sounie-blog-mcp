package nz.sounie.blogmcp.search.domain.embedding;

import java.util.Arrays;
import java.util.Objects;

/**
 * A decoded image as an immutable 8-bit RGB raster: {@code width × height × 3} bytes, row-major
 * from the top-left pixel, channels interleaved R, G, B (search.md 3.6).
 */
public record ImagePixels(int width, int height, byte[] rgb) {

  /** 4,096 × 4,096 pixels (owner decision Q-IMG 1). */
  public static final long MAX_PIXELS = 4096L * 4096L;

  /**
   * @throws InvalidImagePixels if a side is not positive, the image has more than {@link
   *     #MAX_PIXELS} pixels, or the data is not exactly {@code width × height × 3} bytes
   */
  public ImagePixels {
    Objects.requireNonNull(rgb, "rgb");
    ImagePixelsRule.check(width, height, rgb.length);
    rgb = rgb.clone();
  }

  /** A copy of the RGB bytes. */
  @Override
  public byte[] rgb() {
    return rgb.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ImagePixels that
        && width == that.width
        && height == that.height
        && Arrays.equals(rgb, that.rgb);
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, height, Arrays.hashCode(rgb));
  }

  /** The dimensions only; the bytes are never printed. */
  @Override
  public String toString() {
    return "ImagePixels[" + width + " x " + height + "]";
  }
}
