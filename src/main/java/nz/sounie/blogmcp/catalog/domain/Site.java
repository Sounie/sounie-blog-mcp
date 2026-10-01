package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;

/** One configured blog. Immutable reference data; the catalog never creates or changes sites. */
public record Site(SiteId id, Platform platform, URI baseUrl) {

  /** The site host: the host of the base URL. */
  public String host() {
    throw new UnsupportedOperationException("not implemented");
  }
}
