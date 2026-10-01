package nz.sounie.blogmcp.search.domain;

/**
 * The trimmed query text: not blank, at most {@link #MAX_CHARS} characters.
 *
 * <p>The compact constructor trims and throws {@link InvalidSearchQuery} with reason {@code BLANK}
 * or {@code TOO_LONG}.
 */
public record QueryText(String value) {

  public static final int MAX_CHARS = 1000;

  public QueryText {
    value = value.strip();
    if (value.isEmpty()) {
      throw new InvalidSearchQuery(InvalidSearchQuery.Reason.BLANK, "The query is blank");
    }
    if (value.length() > MAX_CHARS) {
      throw new InvalidSearchQuery(
          InvalidSearchQuery.Reason.TOO_LONG,
          "The query has more than " + MAX_CHARS + " characters");
    }
  }
}
