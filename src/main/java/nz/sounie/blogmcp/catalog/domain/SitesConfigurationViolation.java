package nz.sounie.blogmcp.catalog.domain;

/** One reason why a sites configuration is invalid. */
public record SitesConfigurationViolation(Kind kind, String detail) {

  /** The rule that was broken. */
  public enum Kind {
    NO_SITES,
    INVALID_SITE_ID,
    DUPLICATE_SITE_ID,
    UNSUPPORTED_PLATFORM,
    BASE_URL_NOT_ABSOLUTE_HTTPS,
    BASE_URL_HAS_QUERY_OR_FRAGMENT,
    DUPLICATE_BASE_URL
  }
}
