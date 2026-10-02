package nz.sounie.blogmcp.search.domain.query;

import java.util.Objects;

/** A search query that is not valid as a whole. */
final class InvalidSearchQuery extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Why the query was rejected. */
  public enum Reason {
    BLANK,
    TOO_LONG,
    FROM_AFTER_TO
  }

  private final Reason reason;

  public InvalidSearchQuery(Reason reason, String message) {
    super(message);
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public Reason reason() {
    return reason;
  }
}
