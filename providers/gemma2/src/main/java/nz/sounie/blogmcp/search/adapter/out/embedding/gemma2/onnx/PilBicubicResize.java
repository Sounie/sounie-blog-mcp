package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/**
 * A port of PIL's antialiased bicubic {@code Image.resize} on 8-bit RGB (search.md 2.1): a
 * horizontal pass, then a vertical pass over its result, each a convolution with {@link
 * BicubicAxis} coefficients. A pass whose size does not change is the identity, because its
 * kernel's only non-zero tap is the sample itself.
 */
final class PilBicubicResize {

  private static final int CHANNELS = 3;

  private PilBicubicResize() {}

  static ResizedImage resize(ImagePixels pixels, TargetSize target) {
    int height = pixels.height();
    byte[] horizontal =
        horizontal(
            pixels.rgb(), pixels.width(), height, BicubicAxis.of(pixels.width(), target.width()));
    byte[] both = vertical(horizontal, target.width(), BicubicAxis.of(height, target.height()));
    return new ResizedImage(target, new ImagePixels(target.width(), target.height(), both));
  }

  private static byte[] horizontal(byte[] rgb, int width, int height, BicubicAxis axis) {
    int outWidth = axis.outSize();
    byte[] out = new byte[outWidth * height * CHANNELS];
    for (int i = 0; i < out.length; i++) {
      int pixel = i / CHANNELS;
      int rowStart = pixel / outWidth * width * CHANNELS;
      out[i] = axis.sample(pixel % outWidth, rgb, rowStart + i % CHANNELS, CHANNELS);
    }
    return out;
  }

  private static byte[] vertical(byte[] rgb, int width, BicubicAxis axis) {
    int rowLength = width * CHANNELS;
    byte[] out = new byte[rowLength * axis.outSize()];
    for (int i = 0; i < out.length; i++) {
      out[i] = axis.sample(i / rowLength, rgb, i % rowLength, rowLength);
    }
    return out;
  }
}
