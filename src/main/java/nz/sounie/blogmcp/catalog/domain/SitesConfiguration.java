package nz.sounie.blogmcp.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The validated set of all sites. Valid only as a whole. */
public final class SitesConfiguration {

  private SitesConfiguration() {}

  /**
   * Validates the whole list and reports every violation together.
   *
   * @throws InvalidSitesConfiguration listing all violations
   */
  public static SitesConfiguration of(List<SiteDefinition> definitions) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Sites in configuration order. */
  public List<Site> sites() {
    throw new UnsupportedOperationException("not implemented");
  }

  public Optional<Site> find(SiteId id) {
    throw new UnsupportedOperationException("not implemented");
  }

  public Set<SiteId> siteIds() {
    throw new UnsupportedOperationException("not implemented");
  }
}
