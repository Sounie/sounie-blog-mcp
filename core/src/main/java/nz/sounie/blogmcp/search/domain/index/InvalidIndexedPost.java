package nz.sounie.blogmcp.search.domain.index;

/**
 * An indexed post that would break one of its invariants (identity, chunk indexes, or its images'
 * indexes, URLs and descriptions).
 */
public final class InvalidIndexedPost extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidIndexedPost(String message) {
    super(message);
  }
}
