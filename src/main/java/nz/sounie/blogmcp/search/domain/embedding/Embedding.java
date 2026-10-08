package nz.sounie.blogmcp.search.domain.embedding;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * A vector of exactly dimension finite floats, normalised to unit length when created.
 *
 * <p>The compact constructor throws {@link InvalidEmbedding} for a wrong dimension, a non-finite
 * value or the zero vector. It copies the caller's array, and {@link #values()} returns a copy, so
 * an embedding is immutable. Equality compares the values.
 */
public record Embedding(int dimension, float[] values) {
  public Embedding {
    values = unitLength(dimension, withDimension(dimension, values));
  }

  private static float[] withDimension(int dimension,  float[] values) {
    if (values == null || values.length != dimension) {
      throw new InvalidEmbedding("An embedding needs exactly " + dimension + " values");
    }
    return values;
  }

  /** A new array scaled to length 1. */
  private static float[] unitLength(int dimension, float[] values) {
    double norm = norm(dimension, values);
    float[] unit = new float[dimension];
    IntStream.range(0, dimension).forEach(i -> unit[i] = (float) (values[i] / norm));
    return unit;
  }

  /** A non-finite value makes the norm NaN or infinite; finite floats cannot overflow a double. */
  private static double norm(int dimension, float[] values) {
    double norm = Math.sqrt(dot(dimension, values, values));
    if (!Double.isFinite(norm) || norm == 0) {
      throw new InvalidEmbedding("An embedding needs finite values and a non-zero length");
    }
    return norm;
  }

  private static double dot(int dimension, float[] a, float[] b) {
    return IntStream.range(0, dimension).mapToDouble(i -> (double) a[i] * b[i]).sum();
  }

  /** A copy of the unit-length values. */
  @Override
  public float[] values() {
    return values.clone();
  }

  /** Cosine similarity; a dot product, because both are unit length. */
  public Similarity similarityTo(Embedding other) {
    return new Similarity(dot(dimension, values, other.values));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Embedding embedding && Arrays.equals(values, embedding.values);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }
}
