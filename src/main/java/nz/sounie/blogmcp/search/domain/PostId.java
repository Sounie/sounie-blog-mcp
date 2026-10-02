package nz.sounie.blogmcp.search.domain;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Search's copy of a post's identity. External form {@code <siteId>:<sourcePostId>}. */
public record PostId(SiteId siteId, String sourcePostId) {

  private static final char SEPARATOR = ':';

  /** The site part runs to the first colon; the source post ID may contain more colons. */
  private static final Pattern EXTERNAL_FORM = Pattern.compile("([^:]*):(.*)", Pattern.DOTALL);

  /**
   * @throws MalformedCatalogPost if the source post ID is blank
   */
  public PostId {
    Objects.requireNonNull(siteId, "siteId");
    if (sourcePostId == null || sourcePostId.isBlank()) {
      throw new MalformedCatalogPost("Blank source post ID for site " + siteId.value());
    }
  }

  /**
   * Parses the external form.
   *
   * @throws MalformedCatalogPost if the text is missing or not {@code <siteId>:<sourcePostId>}
   */
  public static PostId parse(String external) {
    Matcher parts = EXTERNAL_FORM.matcher(MalformedCatalogPost.requirePresent(external, "post ID"));
    if (!parts.matches()) {
      throw new MalformedCatalogPost("Not a post ID (<siteId>:<sourcePostId>): '" + external + "'");
    }
    return new PostId(new SiteId(parts.group(1)), parts.group(2));
  }

  /** The external form, e.g. {@code sounie-wp:1}. */
  public String external() {
    return siteId.value() + SEPARATOR + sourcePostId;
  }
}
