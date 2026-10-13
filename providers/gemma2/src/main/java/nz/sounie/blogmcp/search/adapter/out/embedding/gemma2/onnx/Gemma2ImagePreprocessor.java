package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/** Image pixels to vision input: target size, then PIL bicubic resize, then patchify. */
final class Gemma2ImagePreprocessor {

  private Gemma2ImagePreprocessor() {}

  static VisionInput preprocess(ImagePixels pixels) {
    TargetSize target = TargetSize.of(pixels.width(), pixels.height());
    return Patchifier.patchify(PilBicubicResize.resize(pixels, target));
  }
}
