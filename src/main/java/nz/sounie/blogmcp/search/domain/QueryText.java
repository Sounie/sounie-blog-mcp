package nz.sounie.blogmcp.search.domain;

/**
 * The trimmed query text: not blank, at most {@link #MAX_CHARS} characters.
 *
 * <p>The compact constructor trims and throws {@link InvalidSearchQuery} with reason {@code BLANK}
 * or {@code TOO_LONG}.
 */
public record QueryText(String value) {

  public static final int MAX_CHARS = 1000;

  public QueryText {}
}
