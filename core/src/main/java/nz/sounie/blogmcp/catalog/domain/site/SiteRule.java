package nz.sounie.blogmcp.catalog.domain.site;

import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind;

/** A rule that one site definition must satisfy on its own. Each yields at most one violation. */
enum SiteRule {
  SITE_ID {
    @Override
    Optional<SitesConfigurationViolation> check(SiteDefinition definition) {
      return violationUnless(
          SiteId.isValid(definition.id()),
          Kind.INVALID_SITE_ID,
          "site ID must match [a-z0-9-]{1,40}: '" + definition.id() + "'");
    }
  },
  SUPPORTED_PLATFORM {
    @Override
    Optional<SitesConfigurationViolation> check(SiteDefinition definition) {
      return violationUnless(
          Platform.named(definition.platform()).isPresent(),
          Kind.UNSUPPORTED_PLATFORM,
          "unsupported platform: '" + definition.platform() + "'");
    }
  },
  /** Absolute https with a host, and no query or fragment. */
  HTTPS_BASE_URL {
    @Override
    Optional<SitesConfigurationViolation> check(SiteDefinition definition) {
      return Site.baseUrlProblem(definition.baseUrl())
          .map(
              kind ->
                  new SitesConfigurationViolation(
                      kind, "invalid base URL: '" + definition.baseUrl() + "'"));
    }
  };

  abstract Optional<SitesConfigurationViolation> check(SiteDefinition definition);

  private static Optional<SitesConfigurationViolation> violationUnless(
      boolean satisfied, Kind kind, String detail) {
    return satisfied
        ? Optional.empty()
        : Optional.of(new SitesConfigurationViolation(kind, detail));
  }
}
