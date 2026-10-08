package nz.sounie.blogmcp.search.adapter.out;

import java.util.Random;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;

/** Embeddings for storage tests: dense, irregular values that exercise every float bit. */
final class TestEmbeddings {

  private TestEmbeddings() {}

  /**
   * A seeded dense vector that also holds negative zero, a subnormal and a tiny normal value, so a
   * lossy encoding would show.
   */
  static Embedding irregular(long seed) {
    Random random = new Random(seed);
    float[] values = new float[384];
    IntStream.range(0, values.length).forEach(i -> values[i] = (float) random.nextGaussian());
    values[1] = -0.0f;
    values[2] = 1.0e-40f;
    values[3] = Float.MIN_NORMAL;
    return new Embedding(384, values);
  }

  /** The raw IEEE-754 bits of every value, for bit-exact comparison. */
  static int[] rawBits(Embedding embedding) {
    float[] values = embedding.values();
    return IntStream.range(0, values.length).map(i -> Float.floatToRawIntBits(values[i])).toArray();
  }
}
