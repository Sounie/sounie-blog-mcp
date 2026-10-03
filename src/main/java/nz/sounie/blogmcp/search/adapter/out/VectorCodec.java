package nz.sounie.blogmcp.search.adapter.out;

/**
 * A chunk's embedding as stored in an index file: the base64 of its little-endian IEEE-754 float32
 * values (384 values, 1,536 bytes). Round-trips bit-exactly. Knows nothing of the dimension; {@code
 * Embedding} checks that.
 */
final class VectorCodec {

  private VectorCodec() {}

  static String encode(float[] values) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /**
   * @throws IllegalArgumentException if the text is not base64, or does not decode to a whole
   *     number of float32 values
   */
  static float[] decode(String base64) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
