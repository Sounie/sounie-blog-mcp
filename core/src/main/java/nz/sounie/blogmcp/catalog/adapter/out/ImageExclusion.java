package nz.sounie.blogmcp.catalog.adapter.out;

/** The rule table of image exclusions: an image is kept only when no rule excludes it. */
enum ImageExclusion {
  /** Class {@code emoji} or {@code wp-smiley}. */
  EMOJI,
  /**
   * A declared {@code width} or {@code height} below 64 (covers tracking pixels). A value that is
   * not a whole number of pixels counts as not declared.
   */
  TOO_SMALL,
  /** The resolved URL is not absolute {@code https} (covers {@code data:}, {@code http:}, none). */
  NOT_HTTPS;

  boolean excludes(ImageCandidate candidate) {
    throw new UnsupportedOperationException("ImageExclusion.excludes is not implemented yet");
  }
}
