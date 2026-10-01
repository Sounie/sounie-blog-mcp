package nz.sounie.blogmcp.catalog.domain;

/** Port: where the sites configuration comes from. */
public interface SiteDirectory {

  /**
   * @throws SitesConfigurationMissing if there is no configuration to load
   * @throws InvalidSitesConfiguration if the configuration breaks any rule
   */
  SitesConfiguration load();
}
