package nz.sounie.blogmcp.search.domain;

/**
 * SHA-256 (lower-case hex) over the index recipe, the normalised title and the normalised body.
 * Metadata is not included.
 */
public record ContentFingerprint(String value) {

  public static ContentFingerprint of(IndexRecipe recipe, String title, WordSequence body) {
    throw new UnsupportedOperationException("not implemented");
  }
}
