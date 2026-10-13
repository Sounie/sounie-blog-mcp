package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/**
 * A target size counted in patches: columns ({@code width / 16}) and rows ({@code height / 16}).
 */
record PatchGrid(int columns, int rows) {

  /** {@code columns × rows}. */
  int realPatches() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** {@code (columns / 3) × (rows / 3)}, after the vision encoder's 3 × 3 pooling. */
  int imageTokens() {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
