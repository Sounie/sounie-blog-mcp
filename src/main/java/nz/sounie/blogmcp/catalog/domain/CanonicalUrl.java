package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Public address of a post: an absolute {@code https} URL. */
public record CanonicalUrl(URI value) {

  private static final int DEFAULT_HTTPS_PORT = 443;

  public CanonicalUrl {
    Objects.requireNonNull(value, "canonical URL");
    if (!"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null) {
      throw new IllegalArgumentException("Canonical URL must be absolute https: " + value);
    }
  }

  /**
   * Parses a link reported by a blog source for the given site. Never upgrades {@code http}.
   *
   * @throws CanonicalUrlNotOnSite unless the link is absolute, {@code https}, and on the site host
   *     (host compared ignoring case)
   */
  public static CanonicalUrl onSite(Site site, String link) {
    return parse(link).requireOn(site);
  }

  /** Whether the host of this URL equals the site host, ignoring case. */
  public boolean isOn(Site site) {
    return value.getHost().equalsIgnoreCase(site.host());
  }

  /**
   * The form used to compare URLs for lookup: lower-case scheme and host, no default port, no
   * fragment, no trailing slash on the path. The query string is kept.
   */
  public String normalisedForm() {
    return "https://"
        + value.getHost().toLowerCase(Locale.ROOT)
        + portPart()
        + pathPart()
        + queryPart();
  }

  private static CanonicalUrl parse(String link) {
    try {
      return new CanonicalUrl(new URI(link));
    } catch (URISyntaxException | IllegalArgumentException | NullPointerException e) {
      throw new CanonicalUrlNotOnSite("Not an absolute https link: '" + link + "'");
    }
  }

  private CanonicalUrl requireOn(Site site) {
    if (!isOn(site)) {
      throw new CanonicalUrlNotOnSite("Link " + value + " is not on the site host " + site.host());
    }
    return this;
  }

  /** Empty for the default port. */
  private String portPart() {
    int port = value.getPort();
    return port == -1 || port == DEFAULT_HTTPS_PORT ? "" : ":" + port;
  }

  /** The raw path without one trailing slash. */
  private String pathPart() {
    String path = Objects.requireNonNullElse(value.getRawPath(), "");
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private String queryPart() {
    return Optional.ofNullable(value.getRawQuery()).map(query -> "?" + query).orElse("");
  }
}
