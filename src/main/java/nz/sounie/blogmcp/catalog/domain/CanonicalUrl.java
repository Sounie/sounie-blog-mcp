package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

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
    CanonicalUrl url;
    try {
      url = new CanonicalUrl(new URI(link));
    } catch (URISyntaxException | IllegalArgumentException | NullPointerException e) {
      throw new CanonicalUrlNotOnSite("Not an absolute https link: '" + link + "'");
    }
    if (!url.isOn(site)) {
      throw new CanonicalUrlNotOnSite("Link " + link + " is not on the site host " + site.host());
    }
    return url;
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
    StringBuilder form =
        new StringBuilder("https://").append(value.getHost().toLowerCase(Locale.ROOT));
    int port = value.getPort();
    if (port != -1 && port != DEFAULT_HTTPS_PORT) {
      form.append(':').append(port);
    }
    String path = Objects.requireNonNullElse(value.getRawPath(), "");
    form.append(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
    if (value.getRawQuery() != null) {
      form.append('?').append(value.getRawQuery());
    }
    return form.toString();
  }
}
