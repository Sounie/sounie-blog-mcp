package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/** The text-model input ids for one image: {@code [2, 255999, 258880 × n, 258882, 1]}. */
final class ImageInputIds {

  private ImageInputIds() {}

  static long[] of(PatchGrid grid) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
