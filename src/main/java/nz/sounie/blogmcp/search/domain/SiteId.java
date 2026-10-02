package nz.sounie.blogmcp.search.domain;

import java.util.regex.Pattern;

/** The site part of a post ID ({@code [a-z0-9-]{1,40}}). Used by the site filter. */
public record SiteId(String value) {

  private static final Pattern FORMAT = Pattern.compile("[a-z0-9-]{1,40}");

  /**
   * @throws MalformedCatalogPost if the value does not match {@code [a-z0-9-]{1,40}}
   */
  public SiteId {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new MalformedCatalogPost("Not a site ID ([a-z0-9-]{1,40}): '" + value + "'");
    }
  }
}
