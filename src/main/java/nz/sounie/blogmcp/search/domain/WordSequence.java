package nz.sounie.blogmcp.search.domain;

import java.util.List;

/**
 * Text after normalisation, as an ordered list of words. Any run of Unicode whitespace separates
 * words; overlong words are split into pieces of at most {@link #MAX_WORD_CHARS} characters.
 */
public record WordSequence(List<String> words) {

  public static final int MAX_WORD_CHARS = 64;

  public static WordSequence of(String text) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** The words joined by single spaces. */
  public String text() {
    throw new UnsupportedOperationException("not implemented");
  }

  public boolean isEmpty() {
    throw new UnsupportedOperationException("not implemented");
  }
}
