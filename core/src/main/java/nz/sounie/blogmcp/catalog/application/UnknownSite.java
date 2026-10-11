package nz.sounie.blogmcp.catalog.application;

import nz.sounie.blogmcp.catalog.domain.site.SiteId;

/** A sync was requested for a site that is not in the sites configuration. */
public final class UnknownSite extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownSite(SiteId siteId) {
    super("Unknown site: " + siteId.value());
  }
}
