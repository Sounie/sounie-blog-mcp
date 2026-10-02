package nz.sounie.blogmcp.catalog.domain.site;

import java.util.List;

/** The sites configuration broke one or more rules. Lists every violation, not just the first. */
public final class InvalidSitesConfiguration extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<SitesConfigurationViolation> violations;

  public InvalidSitesConfiguration(List<SitesConfigurationViolation> violations) {
    super("Invalid sites configuration: " + violations);
    this.violations = List.copyOf(violations);
  }

  public List<SitesConfigurationViolation> violations() {
    return violations;
  }
}
