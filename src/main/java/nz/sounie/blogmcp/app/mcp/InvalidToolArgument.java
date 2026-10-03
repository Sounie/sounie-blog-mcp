package nz.sounie.blogmcp.app.mcp;

/** A tool argument is missing, of the wrong type or form, or not in the tool's schema. */
public final class InvalidToolArgument extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidToolArgument(String message) {
    super(message);
  }
}
