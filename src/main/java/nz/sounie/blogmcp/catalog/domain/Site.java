package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
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

  /** What is wrong with base URL text, if anything; text that is not a URL is not https. */
  static Optional<Kind> baseUrlProblem(String text) {
    return parse(text)
        .map(Site::baseUrlProblem)
        .orElse(Optional.of(Kind.BASE_URL_NOT_ABSOLUTE_HTTPS));
  }

  /** The base URL, if the text is a valid one. */
  static Optional<URI> validBaseUrl(String text) {
    return parse(text).filter(url -> baseUrlProblem(url).isEmpty());
  }

  /** Base URLs that differ only in host case or a trailing slash identify the same site. */
  static String comparableBaseUrl(URI baseUrl) {
    String path = baseUrl.getRawPath();
    String withoutTrailingSlash = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    return baseUrl.getHost().toLowerCase(Locale.ROOT)
        + ":"
        + baseUrl.getPort()
        + withoutTrailingSlash;
  }

  private static Optional<URI> parse(String text) {
    return Optional.ofNullable(text).flatMap(Site::parseUri);
  }

  private static Optional<URI> parseUri(String text) {
    try {
      return Optional.of(new URI(text));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }
}
