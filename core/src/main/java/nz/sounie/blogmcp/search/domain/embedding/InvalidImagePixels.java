package nz.sounie.blogmcp.search.domain.embedding;

/** The rejection of an image-pixels input, with the reason (search.md 3.6). */
public final class InvalidImagePixels extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Why the input was rejected. */
  public enum Reason {
    NON_POSITIVE_DIMENSION,
    TOO_MANY_PIXELS,
    DATA_LENGTH_MISMATCH
  }

  private final Reason reason;

  public InvalidImagePixels(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
