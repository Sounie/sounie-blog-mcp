package nz.sounie.blogmcp.search.domain.embedding;

/** The embedding model could not embed (it failed to load or to run). */
public final class EmbedderUnavailable extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmbedderUnavailable(String message) {
    super(message);
  }

  public EmbedderUnavailable(String message, Throwable cause) {
    super(message, cause);
  }
}
