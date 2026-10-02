package nz.sounie.blogmcp.catalog.domain.post;

/** A snapshot was applied to a post (or site) it does not belong to. */
public final class PostIdentityMismatch extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PostIdentityMismatch(String message) {
    super(message);
  }
}
