package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind;

/** One configured blog. Immutable reference data; the catalog never creates or changes sites. */
public record Site(SiteId id, Platform platform, URI baseUrl) {

  public Site {
    Objects.requireNonNull(id, "site ID");
    Objects.requireNonNull(platform, "platform");
    Objects.requireNonNull(baseUrl, "base URL");
    baseUrlProblem(baseUrl)
        .ifPresent(
            problem -> {
              throw new IllegalArgumentException(problem + ": " + baseUrl);
            });
  }

  /** The site host: the host of the base URL. */
  public String host() {
    return baseUrl.getHost();
  }

  /**
   * What is wrong with a base URL, if anything: it must be absolute {@code https} with a host, and
   * have no query or fragment.
   */
  static Optional<Kind> baseUrlProblem(URI baseUrl) {
    if (!"https".equalsIgnoreCase(baseUrl.getScheme()) || baseUrl.getHost() == null) {
      return Optional.of(Kind.BASE_URL_NOT_ABSOLUTE_HTTPS);
    }
    if (baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
      return Optional.of(Kind.BASE_URL_HAS_QUERY_OR_FRAGMENT);
    }
    return Optional.empty();
  }
}
