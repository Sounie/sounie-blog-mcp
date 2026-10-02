package nz.sounie.blogmcp.search.domain.embedding;

/** A vector that is not a valid 384-dimensional, finite, non-zero embedding. */
final class InvalidEmbedding extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidEmbedding(String message) {
    super(message);
  }
}
