package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/** Image pixels whose width and height equal a target size; the only input the patchifier takes. */
record ResizedImage(TargetSize size, ImagePixels pixels) {

  /**
   * @throws IllegalArgumentException if the pixels are not exactly the target size
   */
  ResizedImage {
    if (!size.equals(new TargetSize(pixels.width(), pixels.height()))) {
      throw new IllegalArgumentException("Expected an image of " + size + ", not " + pixels);
    }
  }
}
