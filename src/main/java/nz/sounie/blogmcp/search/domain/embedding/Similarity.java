package nz.sounie.blogmcp.search.domain.embedding;

/** Cosine similarity of two embeddings, in [-1, 1]. Higher means more related. */
public record Similarity(double value) implements Comparable<Similarity> {

  @Override
  public int compareTo(Similarity other) {
    return Double.compare(value, other.value);
  }
}
