package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code srcset} attribute of an {@code <img>}. Its choice is the candidate with the largest
 * width descriptor ({@code w}) of at most 2048, the first on a tie. Candidates with {@code x}
 * descriptors, no descriptor, or an unparseable one are ignored.
 */
record Srcset(String attribute) {

  private static final int MAX_WIDTH = 2048;

  /**
   * One candidate, read in linear time (anchored with {@code \G}, possessive quantifiers only), as
   * in the HTML spec: separators are skipped; a URL is a run of non-whitespace that may contain
   * commas. A URL ending in a comma has no descriptors (group 1 absent). Otherwise group 1 is the
   * URL and group 2 its descriptors, up to the next comma.
   */
  private static final Pattern CANDIDATE =
      Pattern.compile("\\G[\\s,]*+(?:\\S*+(?<=,)|(\\S++)\\s*+([^,]*+),?+)");

  /**
   * A width descriptor: a whole number of pixels followed by {@code w}. More than nine digits is
   * far wider than {@link #MAX_WIDTH}, so such a candidate is ignored like any other too wide.
   */
  private static final Pattern WIDTH = Pattern.compile("(\\d{1,9})w");

  /** The chosen candidate URL, as written; empty when no candidate qualifies. */
  Optional<String> chosenUrl() {
    return CANDIDATE
        .matcher(attribute)
        .results()
        .flatMap(match -> WidthCandidate.from(match).stream())
        .filter(candidate -> candidate.width() <= MAX_WIDTH)
        .max(Comparator.comparingInt(WidthCandidate::width))
        .map(WidthCandidate::url);
  }

  /** One {@code srcset} candidate with a width descriptor. */
  private record WidthCandidate(String url, int width) {

    /** The candidate, if it has a URL and its descriptors are exactly one width. */
    static Optional<WidthCandidate> from(MatchResult candidate) {
      return Optional.ofNullable(candidate.group(1))
          .flatMap(url -> withWidth(url, candidate.group(2)));
    }

    private static Optional<WidthCandidate> withWidth(String url, String descriptors) {
      Matcher width = WIDTH.matcher(descriptors.strip());
      return width.matches()
          ? Optional.of(new WidthCandidate(url, Integer.parseInt(width.group(1))))
          : Optional.empty();
    }
  }
}
