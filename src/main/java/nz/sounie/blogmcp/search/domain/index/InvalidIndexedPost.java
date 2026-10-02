package nz.sounie.blogmcp.search.domain.index;

/** An indexed post that would break one of its invariants (identity or chunk indexes). */
public final class InvalidIndexedPost extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidIndexedPost(String message) {
    super(message);
  }
}
