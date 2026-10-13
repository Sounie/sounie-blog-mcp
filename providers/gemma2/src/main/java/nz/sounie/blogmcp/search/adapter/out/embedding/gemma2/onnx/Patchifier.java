package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/** Cuts a resized image into rescaled 16 × 16 patches with position ids, padded to the budget. */
final class Patchifier {

  /** The patch budget: 280 image tokens × 9, and the vision encoder's fixed input length. */
  static final int MAX_PATCHES = 2520;

  /** Values in one flattened patch: 16 × 16 pixels × 3 channels. */
  static final int PATCH_VALUES = 768;

  private Patchifier() {}

  static VisionInput patchify(ResizedImage image) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
