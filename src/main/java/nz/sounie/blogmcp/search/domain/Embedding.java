package nz.sounie.blogmcp.search.domain;

/**
 * A vector of exactly {@link #DIMENSION} finite floats, normalised to unit length when created.
 *
 * <p>The compact constructor throws {@link InvalidEmbedding} for a wrong dimension, a non-finite
 * value or the zero vector.
 */
public record Embedding(float[] values) {

  public static final int DIMENSION = 384;

  public Embedding {}

  /** Cosine similarity; a dot product, because both are unit length. */
  public Similarity similarityTo(Embedding other) {
    throw new UnsupportedOperationException("not implemented");
  }
}
