package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/** Image pixels whose width and height equal a target size; the only input the patchifier takes. */
record ResizedImage(TargetSize size, ImagePixels pixels) {

  ResizedImage {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
