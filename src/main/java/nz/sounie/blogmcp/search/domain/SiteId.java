package nz.sounie.blogmcp.search.domain;

/** The site part of a post ID ({@code [a-z0-9-]{1,40}}). Used by the site filter. */
public record SiteId(String value) {

  /**
   * @throws MalformedCatalogPost if the value does not match {@code [a-z0-9-]{1,40}}
   */
  public SiteId {}
}
