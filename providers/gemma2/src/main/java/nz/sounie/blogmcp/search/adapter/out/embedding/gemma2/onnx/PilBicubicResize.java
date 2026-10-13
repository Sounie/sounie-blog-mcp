package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/** A port of PIL's antialiased bicubic {@code Image.resize} on 8-bit RGB (search.md 2.1). */
final class PilBicubicResize {

  private PilBicubicResize() {}

  static ResizedImage resize(ImagePixels pixels, TargetSize target) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
