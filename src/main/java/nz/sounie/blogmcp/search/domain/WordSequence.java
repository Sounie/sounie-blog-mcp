package nz.sounie.blogmcp.search.domain;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Text after normalisation, as an ordered list of words. Any run of Unicode whitespace separates
 * words; overlong words are split into pieces of at most {@link #MAX_WORD_CHARS} characters.
 */
public record WordSequence(List<String> words) {

  public static final int MAX_WORD_CHARS = 64;

  /**
   * Unicode {@code White_Space} (including the no-break space) plus the separators U+001C..U+001F
   * that {@link Character#isWhitespace} also counts, so no word is ever blank to the model library.
   */
  private static final String WHITE_SPACE = "[\\p{IsWhite_Space}\\x{1C}-\\x{1F}]+";

  private static final Pattern WHITESPACE = Pattern.compile(WHITE_SPACE);

  private static final Pattern SURROUNDING_WHITE_SPACE =
      Pattern.compile("^" + WHITE_SPACE + "|" + WHITE_SPACE + "$");

  public WordSequence {
    words = List.copyOf(words);
  }

  public static WordSequence of(String text) {
    return new WordSequence(WHITESPACE.splitAsStream(text).flatMap(WordSequence::pieces).toList());
  }

  /**
   * Consecutive pieces of at most {@link #MAX_WORD_CHARS} characters (code points). The empty
   * string left by leading whitespace has no pieces.
   */
  private static Stream<String> pieces(String word) {
    int[] codePoints = word.codePoints().toArray();
    return IntStream.iterate(0, start -> start < codePoints.length, start -> start + MAX_WORD_CHARS)
        .mapToObj(
            start ->
                new String(codePoints, start, Math.min(MAX_WORD_CHARS, codePoints.length - start)));
  }

  /** The text without leading or trailing white space, by the same rule that separates words. */
  static String strip(String text) {
    return SURROUNDING_WHITE_SPACE.matcher(text).replaceAll("");
  }

  /** The words joined by single spaces. */
  public String text() {
    return String.join(" ", words);
  }

  public boolean isEmpty() {
    return words.isEmpty();
  }
}
