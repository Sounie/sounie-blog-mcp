package nz.sounie.blogmcp.search.domain.embedding;

/**
 * Unit-length test vectors with chosen similarities. The query is axis 0; {@link #atSimilarity}
 * returns {@code s·e0 + sqrt(1−s²)·e_k}, whose similarity with the query is exactly {@code s}.
 */
public final class Vectors {

  private Vectors() {}

  public static float[] axisValues(int dimension, int axis) {
    float[] v = new float[dimension];
    v[axis] = 1f;
    return v;
  }

  public static Embedding axis(int dimension, int axis) {
    return new Embedding(dimension, axisValues(dimension, axis));
  }

  /** The query used with {@link #atSimilarity}. */
  public static Embedding query() {
    return axis(384, 0);
  }

  public static Embedding atSimilarity(int dimension, double similarity) {
    return atSimilarity(dimension, similarity, 1);
  }

  public static Embedding atSimilarity(int dimension, double similarity, int otherAxis) {
    float[] v = new float[dimension];
    v[0] = (float) similarity;
    v[otherAxis] = (float) Math.sqrt(1 - similarity * similarity);
    return new Embedding(dimension, v);
  }
}
