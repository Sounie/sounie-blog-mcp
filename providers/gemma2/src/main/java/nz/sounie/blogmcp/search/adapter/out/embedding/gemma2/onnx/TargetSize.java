package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/**
 * The size an image is resized to before patchifying (search.md 2.1). {@link #of} always gives
 * positive multiples of 48 within the patch budget; the constructor itself accepts any size, so the
 * resize can be tested on small targets.
 */
record TargetSize(int width, int height) {

  /** The target size for an image of this size, as the reference processor computes it. */
  static TargetSize of(int width, int height) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** This size counted in 16-pixel patches. */
  PatchGrid grid() {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
