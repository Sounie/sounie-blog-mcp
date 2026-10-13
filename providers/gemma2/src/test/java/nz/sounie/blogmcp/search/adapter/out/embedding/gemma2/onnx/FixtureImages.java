package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/**
 * Decodes a test fixture into {@link ImagePixels} the way PIL's {@code convert("RGB")} does for the
 * fixtures (all 8-bit RGB): the first three samples of each pixel, any alpha ignored, never painted
 * onto a background (search.md, image embedding ACs). Test code only; phase 1 has no decoder.
 */
final class FixtureImages {

  private FixtureImages() {}

  static ImagePixels decode(String file) {
    Raster raster = read(file).getRaster();
    if (raster.getNumBands() < 3) {
      throw new IllegalArgumentException(file + " is not an RGB fixture");
    }
    int width = raster.getWidth();
    int height = raster.getHeight();
    byte[] rgb = new byte[width * height * 3];
    int[] samples = new int[raster.getNumBands()];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        raster.getPixel(x, y, samples);
        int offset = (y * width + x) * 3;
        rgb[offset] = (byte) samples[0];
        rgb[offset + 1] = (byte) samples[1];
        rgb[offset + 2] = (byte) samples[2];
      }
    }
    return new ImagePixels(width, height, rgb);
  }

  private static BufferedImage read(String file) {
    try (InputStream in = FixtureImages.class.getResourceAsStream("/" + file)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture " + file);
      }
      BufferedImage image = ImageIO.read(in);
      if (image == null) {
        throw new IllegalStateException("No ImageIO reader for " + file);
      }
      return image;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
