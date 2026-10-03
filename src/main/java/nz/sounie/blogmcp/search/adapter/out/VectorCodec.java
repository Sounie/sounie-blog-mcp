package nz.sounie.blogmcp.search.adapter.out;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Base64;

/**
 * A chunk's embedding as stored in an index file: the base64 of its little-endian IEEE-754 float32
 * values (384 values, 1,536 bytes). Round-trips bit-exactly. Knows nothing of the dimension; {@code
 * Embedding} checks that.
 */
final class VectorCodec {

  private VectorCodec() {}

  static String encode(float[] values) {
    ByteBuffer bytes = littleEndian(ByteBuffer.allocate(values.length * Float.BYTES));
    bytes.asFloatBuffer().put(values);
    return Base64.getEncoder().encodeToString(bytes.array());
  }

  /**
   * @throws IllegalArgumentException if the text is not base64, or does not decode to a whole
   *     number of float32 values
   */
  static float[] decode(String base64) {
    byte[] bytes = Base64.getDecoder().decode(base64);
    if (bytes.length % Float.BYTES != 0) {
      throw new IllegalArgumentException(
          "A vector of " + bytes.length + " bytes is not a whole number of float32 values");
    }
    FloatBuffer floats = littleEndian(ByteBuffer.wrap(bytes)).asFloatBuffer();
    float[] values = new float[floats.remaining()];
    floats.get(values);
    return values;
  }

  private static ByteBuffer littleEndian(ByteBuffer bytes) {
    return bytes.order(ByteOrder.LITTLE_ENDIAN);
  }
}
