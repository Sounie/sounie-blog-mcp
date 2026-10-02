package nz.sounie.blogmcp.catalog.domain.post;

/**
 * A post reference (e.g. given to the GetPost use case) is neither a post ID nor an absolute
 * http(s) URL.
 */
public final class InvalidPostReference extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidPostReference(String message) {
    super(message);
  }
}
