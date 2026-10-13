package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/** Builds small synthetic {@link ImagePixels} for the preprocessing tests. */
final class TestImages {

  private TestImages() {}

  /** The colour of the pixel at (x, y), as three 0..255 channel values. */
  @FunctionalInterface
  interface Painter {
    int[] rgbAt(int x, int y);
  }

  static ImagePixels painted(int width, int height, Painter painter) {
    byte[] rgb = new byte[width * height * 3];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int[] colour = painter.rgbAt(x, y);
        int offset = (y * width + x) * 3;
        rgb[offset] = (byte) colour[0];
        rgb[offset + 1] = (byte) colour[1];
        rgb[offset + 2] = (byte) colour[2];
      }
    }
    return new ImagePixels(width, height, rgb);
  }

  static ImagePixels uniform(int width, int height, int red, int green, int blue) {
    return painted(width, height, (x, y) -> new int[] {red, green, blue});
  }

  /**
   * Channel {@code c} (0 = R, 1 = G, 2 = B) of the pixel at (x, y) in a row-major RGB array of the
   * given width, as 0..255. Takes the array, not {@link ImagePixels}, because {@code rgb()} copies.
   */
  static int sample(byte[] rgb, int width, int x, int y, int c) {
    return Byte.toUnsignedInt(rgb[(y * width + x) * 3 + c]);
  }
}
