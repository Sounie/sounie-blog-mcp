package nz.sounie.blogmcp.catalog.domain.site;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The validated set of all sites. Valid only as a whole. */
public final class SitesConfiguration {

  private final List<Site> sites;

  private SitesConfiguration(List<Site> sites) {
    this.sites = List.copyOf(sites);
  }

  /**
   * Validates the whole list and reports every violation together.
   *
   * @throws InvalidSitesConfiguration listing all violations
   */
  public static SitesConfiguration of(List<SiteDefinition> definitions) {
    List<SitesConfigurationViolation> violations = violationsIn(definitions);
    if (!violations.isEmpty()) {
      throw new InvalidSitesConfiguration(violations);
    }
    return new SitesConfiguration(definitions.stream().map(SitesConfiguration::toSite).toList());
  }

  /**
   * Validates the sites and the sync interval setting, and reports every violation together.
   *
   * @throws InvalidSitesConfiguration listing all violations
   */
  public static SitesConfiguration of(
      List<SiteDefinition> definitions, SyncIntervalSetting syncInterval) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
  }

  /** How long to wait between sync-and-reconcile runs. */
  public SyncInterval syncInterval() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
  }

  /** Sites in configuration order. */
  public List<Site> sites() {
    return sites;
  }

  public Optional<Site> find(SiteId id) {
    return sites.stream().filter(site -> site.id().equals(id)).findFirst();
  }

  public Set<SiteId> siteIds() {
    Set<SiteId> ids =
        sites.stream().map(Site::id).collect(Collectors.toCollection(LinkedHashSet::new));
    return Collections.unmodifiableSet(ids);
  }

  private static List<SitesConfigurationViolation> violationsIn(List<SiteDefinition> definitions) {
    Stream<SitesConfigurationViolation> acrossSites =
        Arrays.stream(ConfigurationRule.values()).flatMap(rule -> rule.check(definitions).stream());
    Stream<SitesConfigurationViolation> perSite =
        definitions.stream()
            .flatMap(
                definition ->
                    Arrays.stream(SiteRule.values())
                        .flatMap(rule -> rule.check(definition).stream()));
    return Stream.concat(acrossSites, perSite).toList();
  }

  /** Only called once every rule has passed, so each part is known to be valid. */
  private static Site toSite(SiteDefinition definition) {
    return new Site(
        new SiteId(definition.id()),
        Platform.named(definition.platform()).orElseThrow(),
        URI.create(definition.baseUrl()));
  }
}
