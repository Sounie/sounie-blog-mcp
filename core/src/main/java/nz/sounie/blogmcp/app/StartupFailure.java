package nz.sounie.blogmcp.app;

/** The server cannot start; the message is the one line reported on stderr (AC-APP-8). */
public final class StartupFailure extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StartupFailure(String message) {
    super(message);
  }
}
