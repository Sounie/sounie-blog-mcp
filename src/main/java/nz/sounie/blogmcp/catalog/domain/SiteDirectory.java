package nz.sounie.blogmcp.catalog.domain;

import nz.sounie.blogmcp.catalog.domain.site.InvalidSitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationMissing;

/** Port: where the sites configuration comes from. */
public interface SiteDirectory {

  /**
   * @throws SitesConfigurationMissing if there is no configuration to load
   * @throws InvalidSitesConfiguration if the configuration breaks any rule
   */
  SitesConfiguration load();
}
