package nz.sounie.blogmcp.search.domain.embedding;

import java.util.Arrays;
import nz.sounie.blogmcp.search.domain.embedding.InvalidImagePixels.Reason;

/**
 * The rules {@link ImagePixels} must satisfy, checked in declaration order so the first broken rule
 * names the reason (search.md 3.6, AC-SRCH-54).
 */
enum ImagePixelsRule {
  POSITIVE_DIMENSIONS(Reason.NON_POSITIVE_DIMENSION) {
    @Override
    boolean brokenBy(int width, int height, int length) {
      return width <= 0 || height <= 0;
    }
  },

  /** Counted in {@code long}, so an {@code int} overflow cannot slip under the cap. */
  AT_MOST_MAX_PIXELS(Reason.TOO_MANY_PIXELS) {
    @Override
    boolean brokenBy(int width, int height, int length) {
      return (long) width * height > ImagePixels.MAX_PIXELS;
    }
  },

  THREE_BYTES_PER_PIXEL(Reason.DATA_LENGTH_MISMATCH) {
    @Override
    boolean brokenBy(int width, int height, int length) {
      return (long) width * height * 3 != length;
    }
  };

  private final Reason reason;

  ImagePixelsRule(Reason reason) {
    this.reason = reason;
  }

  abstract boolean brokenBy(int width, int height, int length);

  /**
   * @throws InvalidImagePixels for the first rule this input breaks
   */
  static void check(int width, int height, int length) {
    Arrays.stream(values())
        .filter(rule -> rule.brokenBy(width, height, length))
        .findFirst()
        .ifPresent(
            rule -> {
              throw new InvalidImagePixels(
                  rule.reason,
                  "Image of " + width + " x " + height + " with " + length + " bytes: " + rule);
            });
  }
}
