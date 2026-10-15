package nz.sounie.blogmcp.search.domain.image;

import java.util.Objects;

/** An image query that cannot be used: the text is not a usable local image or https URL. */
public final class InvalidImageQuery extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Why the image query was rejected (search.md glossary "Invalid image query"). */
  public enum Reason {
    NOT_ABSOLUTE_PATH,
    UNSUPPORTED_EXTENSION,
    NOT_A_READABLE_FILE,
    FILE_TOO_LARGE,
    NOT_HTTPS,
    MALFORMED_URL
  }

  private final Reason reason;

  public InvalidImageQuery(Reason reason, String message) {
    super(message);
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public Reason reason() {
    return reason;
  }
}
