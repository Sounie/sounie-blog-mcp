package nz.sounie.blogmcp.catalog.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
      return repeats(
          validIds, Function.identity(), Kind.DUPLICATE_SITE_ID, "site ID used more than once: ");
    }
  },
  UNIQUE_BASE_URLS {
    @Override
    List<SitesConfigurationViolation> check(List<SiteDefinition> definitions) {
      Stream<String> validBaseUrls =
          definitions.stream()
              .map(SiteDefinition::baseUrl)
              .filter(text -> Site.validBaseUrl(text).isPresent());
      return repeats(
          validBaseUrls,
          ConfigurationRule::comparableBaseUrl,
          Kind.DUPLICATE_BASE_URL,
          "base URL used more than once: ");
    }
  };

  abstract List<SitesConfigurationViolation> check(List<SiteDefinition> definitions);

  /**
   * One violation for every value whose key was already seen earlier in the stream. The detail
   * shows the value as the owner wrote it, never the key.
   */
  private static List<SitesConfigurationViolation> repeats(
      Stream<String> values, Function<String, String> key, Kind kind, String label) {
    Set<String> seenKeys = new HashSet<>();
    return values
        .filter(value -> !seenKeys.add(key.apply(value)))
        .map(value -> new SitesConfigurationViolation(kind, label + value))
        .toList();
  }

  /** Only called for text already known to be a valid base URL. */
  private static String comparableBaseUrl(String validBaseUrl) {
    return Site.validBaseUrl(validBaseUrl).map(Site::comparableBaseUrl).orElseThrow();
  }
}
