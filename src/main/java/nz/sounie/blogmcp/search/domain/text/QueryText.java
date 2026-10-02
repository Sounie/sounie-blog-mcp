package nz.sounie.blogmcp.search.domain.text;

/**
 * The trimmed query text: not blank, at most {@link #MAX_CHARS} characters.
 *
 * <p>The compact constructor trims white space (as {@link WordSequence} defines it, so including
 * no-break spaces) and throws {@link InvalidSearchQuery} with reason {@code BLANK} or {@code
 * TOO_LONG}.
 */
public record QueryText(String value) {

  public static final int MAX_CHARS = 1000;

  public QueryText {
    value = WordSequence.strip(value);
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
