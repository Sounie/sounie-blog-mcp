package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/**
 * A target size counted in patches: columns ({@code width / 16}) and rows ({@code height / 16}).
 */
record PatchGrid(int columns, int rows) {

  /** The vision encoder pools 3 × 3 patches into one image token. */
  static final int POOLING = 3;

  /** {@code columns × rows}. */
  int realPatches() {
    return columns * rows;
  }

  /** {@code (columns / 3) × (rows / 3)}, after the vision encoder's 3 × 3 pooling. */
  int imageTokens() {
    return (columns / POOLING) * (rows / POOLING);
  }
}
