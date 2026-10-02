package nz.sounie.blogmcp.search.domain;

/**
 * Unit-length test vectors with chosen similarities. The query is axis 0; {@link #atSimilarity}
 * returns {@code s·e0 + sqrt(1−s²)·e_k}, whose similarity with the query is exactly {@code s}.
 */
public final class Vectors {

  private Vectors() {}

  public static float[] axisValues(int axis) {
    float[] v = new float[Embedding.DIMENSION];
    v[axis] = 1f;
    return v;
  }

  public static Embedding axis(int axis) {
    return new Embedding(axisValues(axis));
  }

  /** The query used with {@link #atSimilarity}. */
  public static Embedding query() {
    return axis(0);
  }

  public static Embedding atSimilarity(double similarity) {
    return atSimilarity(similarity, 1);
  }

  public static Embedding atSimilarity(double similarity, int otherAxis) {
    float[] v = new float[Embedding.DIMENSION];
    v[0] = (float) similarity;
    v[otherAxis] = (float) Math.sqrt(1 - similarity * similarity);
    return new Embedding(v);
  }
}
