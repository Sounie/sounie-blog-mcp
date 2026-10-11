package nz.sounie.blogmcp.catalog.domain.post;

import java.util.Objects;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;

/** Catalog-wide identity of a post. External form {@code <siteId>:<sourcePostId>}. */
public record PostId(SiteId siteId, SourcePostId sourcePostId) {

  private static final char SEPARATOR = ':';

  public PostId {
    Objects.requireNonNull(siteId, "site ID");
    Objects.requireNonNull(sourcePostId, "source post ID");
  }

  /**
   * Parses the external form.
   *
   * @throws IllegalArgumentException if the text is not {@code <siteId>:<sourcePostId>}
   */
  public static PostId parse(String external) {
    int separator = external.indexOf(SEPARATOR);
    if (separator < 0) {
      throw new IllegalArgumentException("Not a post ID (<siteId>:<sourcePostId>): " + external);
    }
    return new PostId(
        new SiteId(external.substring(0, separator)),
        new SourcePostId(external.substring(separator + 1)));
  }

  /** The external form, e.g. {@code sounie-wp:123}. */
  public String external() {
    return siteId.value() + SEPARATOR + sourcePostId.value();
  }
}
