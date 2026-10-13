package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/**
 * The q8 {@code vision_encoder_quantized} graph in its own ONNX Runtime session (search.md 2.1).
 * Outputs {@code image_features [n, 512]}, where {@code n} must equal the patch grid's image
 * tokens.
 */
final class Gemma2VisionEncoder {

  private Gemma2VisionEncoder() {}

  /**
   * Returns the features if their row count equals the grid's image tokens.
   *
   * @throws IllegalStateException if it does not
   */
  static float[][] checkedFeatures(float[][] imageFeatures, PatchGrid grid) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
