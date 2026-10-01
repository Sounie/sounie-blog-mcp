package nz.sounie.blogmcp.catalog.domain;

/** A blog source could not be read (transport failure or HTTP error). */
public final class SourceUnavailable extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SourceUnavailable(String message) {
    super(message);
  }

  public SourceUnavailable(String message, Throwable cause) {
    super(message, cause);
  }
}
