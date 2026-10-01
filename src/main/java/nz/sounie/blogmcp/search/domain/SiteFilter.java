package nz.sounie.blogmcp.search.domain;

/** Which sites a search covers. */
public sealed interface SiteFilter {

  boolean includes(SiteId siteId);

  /** Every site. */
  record AnySite() implements SiteFilter {
    @Override
    public boolean includes(SiteId siteId) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** One site only. */
  record OnlySite(SiteId siteId) implements SiteFilter {
    @Override
    public boolean includes(SiteId candidate) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
