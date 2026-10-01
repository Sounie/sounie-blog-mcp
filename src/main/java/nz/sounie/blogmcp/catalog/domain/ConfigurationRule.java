package nz.sounie.blogmcp.catalog.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind;

/**
 * A rule across all site definitions. Duplicates are reported once per repeat, and only among
 * values that are otherwise valid, so one bad value is not reported twice.
 */
enum ConfigurationRule {
  AT_LEAST_ONE_SITE {
    @Override
    List<SitesConfigurationViolation> check(List<SiteDefinition> definitions) {
      return definitions.isEmpty()
          ? List.of(
              new SitesConfigurationViolation(
                  Kind.NO_SITES, "at least one site must be configured"))
          : List.of();
    }
  },
  UNIQUE_SITE_IDS {
    @Override
    List<SitesConfigurationViolation> check(List<SiteDefinition> definitions) {
      Stream<String> validIds =
          definitions.stream().map(SiteDefinition::id).filter(SiteId::isValid);
      return repeats(validIds, Kind.DUPLICATE_SITE_ID, "site ID used more than once: ");
    }
  },
  UNIQUE_BASE_URLS {
    @Override
    List<SitesConfigurationViolation> check(List<SiteDefinition> definitions) {
      Stream<String> validBaseUrls =
          definitions.stream()
              .map(SiteDefinition::baseUrl)
              .map(Site::validBaseUrl)
              .flatMap(Optional::stream)
              .map(Site::comparableBaseUrl);
      return repeats(validBaseUrls, Kind.DUPLICATE_BASE_URL, "base URL used more than once: ");
    }
  };

  abstract List<SitesConfigurationViolation> check(List<SiteDefinition> definitions);

  /** One violation for every value that was already seen earlier in the stream. */
  private static List<SitesConfigurationViolation> repeats(
      Stream<String> values, Kind kind, String label) {
    Set<String> seen = new HashSet<>();
    return values
        .filter(value -> !seen.add(value))
        .map(value -> new SitesConfigurationViolation(kind, label + value))
        .toList();
  }
}
