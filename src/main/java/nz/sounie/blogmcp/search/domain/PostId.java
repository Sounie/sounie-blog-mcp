package nz.sounie.blogmcp.search.domain;

/** Search's copy of a post's identity. External form {@code <siteId>:<sourcePostId>}. */
public record PostId(SiteId siteId, String sourcePostId) {

  /**
   * @throws MalformedCatalogPost if the source post ID is blank
   */
  public PostId {}

  /**
   * Parses the external form.
   *
   * @throws MalformedCatalogPost if the text is not {@code <siteId>:<sourcePostId>}
   */
  public static PostId parse(String external) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** The external form, e.g. {@code sounie-wp:1}. */
  public String external() {
    throw new UnsupportedOperationException("not implemented");
  }
}
