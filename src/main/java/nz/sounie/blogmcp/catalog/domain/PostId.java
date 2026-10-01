package nz.sounie.blogmcp.catalog.domain;

/** Catalog-wide identity of a post. External form {@code <siteId>:<sourcePostId>}. */
public record PostId(SiteId siteId, SourcePostId sourcePostId) {

  /**
   * Parses the external form.
   *
   * @throws IllegalArgumentException if the text is not {@code <siteId>:<sourcePostId>}
   */
  public static PostId parse(String external) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** The external form, e.g. {@code sounie-wp:123}. */
  public String external() {
    throw new UnsupportedOperationException("not implemented");
  }
}
