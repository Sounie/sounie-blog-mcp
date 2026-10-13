package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/**
 * The size of the image between {@link PilBicubicResize}'s two passes: the target width by the
 * source height (search.md 3.6). It holds at most {@link #MAX_SAMPLES} samples, the same 48 MiB
 * bound as {@link ImagePixels}; only a very tall, narrow image whose width is upscaled to 48 can
 * exceed it.
 */
record IntermediateSize(int width, int height) {

  static final int CHANNELS = 3;

  /** 16,777,216 pixels × 3 channels: 48 MiB. */
  static final long MAX_SAMPLES = ImagePixels.MAX_PIXELS * CHANNELS;

  /**
   * @throws ImageTooLargeToResize if the image would hold more than {@link #MAX_SAMPLES} samples,
   *     computed in {@code long} so it cannot overflow
   */
  IntermediateSize {
    if ((long) width * height * CHANNELS > MAX_SAMPLES) {
      throw new ImageTooLargeToResize(width, height);
    }
  }

  /** The number of samples, which fits an {@code int} because of the bound. */
  int samples() {
    return width * height * CHANNELS;
  }
}
