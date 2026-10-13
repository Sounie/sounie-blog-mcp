package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The rule table of image exclusions: an image is kept only when no rule excludes it. */
enum ImageExclusion {
  /** Class {@code emoji} or {@code wp-smiley}. */
  EMOJI {
    private static final Set<String> EMOJI_CLASSES = Set.of("emoji", "wp-smiley");

    @Override
    boolean excludes(ImageCandidate candidate) {
      return Arrays.stream(candidate.classNames().split("\\s+")).anyMatch(EMOJI_CLASSES::contains);
    }
  },
  /**
   * A declared {@code width} or {@code height} below 64 (covers tracking pixels). A value that is
   * not a whole number of pixels counts as not declared.
   */
  TOO_SMALL {
    private static final int MIN_SIZE = 64;
    private static final Pattern WHOLE_PIXELS = Pattern.compile("\\d{1,9}");

    @Override
    boolean excludes(ImageCandidate candidate) {
      return Stream.of(candidate.width(), candidate.height())
          .flatMap(size -> declaredPixels(size).stream())
          .anyMatch(pixels -> pixels < MIN_SIZE);
    }

    private Optional<Integer> declaredPixels(String size) {
      return Optional.of(size.strip())
          .filter(value -> WHOLE_PIXELS.matcher(value).matches())
          .map(Integer::valueOf);
    }
  },
  /** The resolved URL is not absolute {@code https} (covers {@code data:}, {@code http:}, none). */
  NOT_HTTPS {
    @Override
    boolean excludes(ImageCandidate candidate) {
      return candidate.resolvedUrl().filter(this::isHttpsWithHost).isEmpty();
    }

    private boolean isHttpsWithHost(URI url) {
      return "https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null;
    }
  };

  abstract boolean excludes(ImageCandidate candidate);

  /** Whether no rule excludes the candidate. */
  static boolean keeps(ImageCandidate candidate) {
    return Arrays.stream(values()).noneMatch(rule -> rule.excludes(candidate));
  }
}
