package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;

/** Public address of a post: an absolute {@code https} URL. */
public record CanonicalUrl(URI value) {

  /**
   * Parses a link reported by a blog source for the given site. Never upgrades {@code http}.
   *
   * @throws CanonicalUrlNotOnSite unless the link is absolute, {@code https}, and on the site host
   *     (host compared ignoring case)
   */
  public static CanonicalUrl onSite(Site site, String link) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Whether the host of this URL equals the site host, ignoring case. */
  public boolean isOn(Site site) {
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * The form used to compare URLs for lookup: lower-case scheme and host, no default port, no
   * fragment, no trailing slash on the path. The query string is kept.
   */
  public String normalisedForm() {
    throw new UnsupportedOperationException("not implemented");
  }
}
