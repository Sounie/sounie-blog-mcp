package nz.sounie.blogmcp.catalog.domain;

import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.BASE_URL_HAS_QUERY_OR_FRAGMENT;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.BASE_URL_NOT_ABSOLUTE_HTTPS;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.DUPLICATE_BASE_URL;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.DUPLICATE_SITE_ID;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.INVALID_SITE_ID;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.NO_SITES;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.UNSUPPORTED_PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.net.URI;
import java.util.List;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SitesConfigurationTest {

  @Test
  void a_valid_configuration_holds_its_sites_in_order() {
    SitesConfiguration configuration =
        SitesConfiguration.of(
            List.of(TestSites.SOUNIE_WP_DEFINITION, TestSites.ELEGANT_DEFINITION));

    assertThat(configuration.sites()).containsExactly(TestSites.SOUNIE_WP, TestSites.ELEGANT);
    assertThat(configuration.siteIds())
        .containsExactlyInAnyOrder(TestSites.SOUNIE_WP_ID, TestSites.ELEGANT_ID);
  }

  @Test
  void finds_a_site_by_id() {
    SitesConfiguration configuration =
        SitesConfiguration.of(
            List.of(TestSites.SOUNIE_WP_DEFINITION, TestSites.ELEGANT_DEFINITION));

    assertThat(configuration.find(TestSites.ELEGANT_ID)).contains(TestSites.ELEGANT);
    assertThat(configuration.find(new SiteId("unknown"))).isEmpty();
  }

  @Test
  void a_base_url_with_a_path_is_valid() {
    SitesConfiguration configuration =
        SitesConfiguration.of(
            List.of(new SiteDefinition("blog", "WORDPRESS", "https://example.org/blog")));

    assertThat(configuration.sites().getFirst().baseUrl())
        .isEqualTo(URI.create("https://example.org/blog"));
  }

  @Test
  @DisplayName("AC-CAT-25: every violation is reported together")
  void reports_duplicate_id_unsupported_platform_and_non_https_base_url_together() {
    List<SiteDefinition> definitions =
        List.of(
            new SiteDefinition("blog", "ghost", "https://ghost.example"),
            new SiteDefinition("blog", "WORDPRESS", "http://blog2.sounie.nz"));

    assertThat(violationKinds(definitions))
        .containsExactlyInAnyOrder(
            DUPLICATE_SITE_ID, UNSUPPORTED_PLATFORM, BASE_URL_NOT_ABSOLUTE_HTTPS);
  }

  @Test
  @DisplayName("AC-CAT-25: an empty site list is invalid")
  void an_empty_site_list_is_invalid() {
    assertThat(violationKinds(List.of())).containsExactly(NO_SITES);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Blog", "my_blog", "", "01234567890123456789012345678901234567890"})
  @DisplayName("AC-CAT-25: a site ID must match [a-z0-9-]{1,40}")
  void a_site_id_that_is_not_a_slug_is_invalid(String id) {
    assertThat(violationKinds(List.of(new SiteDefinition(id, "WORDPRESS", "https://a.example"))))
        .containsExactly(INVALID_SITE_ID);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://a.example/?blog=1", "https://a.example/#top"})
  @DisplayName("AC-CAT-25: a base URL must have no query or fragment")
  void a_base_url_with_a_query_or_fragment_is_invalid(String baseUrl) {
    assertThat(violationKinds(List.of(new SiteDefinition("blog", "WORDPRESS", baseUrl))))
        .containsExactly(BASE_URL_HAS_QUERY_OR_FRAGMENT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://a.example", "a.example", "/blog", "https://", "not a url"})
  void a_base_url_must_be_absolute_https_with_a_host(String baseUrl) {
    assertThat(violationKinds(List.of(new SiteDefinition("blog", "WORDPRESS", baseUrl))))
        .containsExactly(BASE_URL_NOT_ABSOLUTE_HTTPS);
  }

  @Test
  @DisplayName("AC-CAT-25: two sites with the same base URL are invalid")
  void two_sites_with_the_same_base_url_are_invalid() {
    List<SiteDefinition> definitions =
        List.of(
            new SiteDefinition("one", "WORDPRESS", "https://blog2.sounie.nz"),
            new SiteDefinition("two", "BLOGGER", "https://blog2.sounie.nz"));

    assertThat(violationKinds(definitions)).containsExactly(DUPLICATE_BASE_URL);
  }

  @Test
  void a_missing_platform_is_unsupported_rather_than_a_crash() {
    assertThat(violationKinds(List.of(new SiteDefinition("blog", null, "https://a.example"))))
        .containsExactly(UNSUPPORTED_PLATFORM);
  }

  @ParameterizedTest
  @ValueSource(strings = {"wordpress", "WordPress", "WORDPRESS"})
  void platform_names_are_matched_ignoring_case_for_wordpress(String platform) {
    SitesConfiguration configuration =
        SitesConfiguration.of(List.of(new SiteDefinition("blog", platform, "https://a.example")));

    assertThat(configuration.sites().getFirst().platform()).isEqualTo(Platform.WORDPRESS);
  }

  @ParameterizedTest
  @ValueSource(strings = {"blogger", "Blogger", "BLOGGER"})
  void platform_names_are_matched_ignoring_case_for_blogger(String platform) {
    SitesConfiguration configuration =
        SitesConfiguration.of(List.of(new SiteDefinition("blog", platform, "https://a.example")));

    assertThat(configuration.sites().getFirst().platform()).isEqualTo(Platform.BLOGGER);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ghost", "GHOST", "word press", ""})
  void an_unknown_platform_name_is_unsupported_in_any_case(String platform) {
    assertThat(violationKinds(List.of(new SiteDefinition("blog", platform, "https://a.example"))))
        .containsExactly(UNSUPPORTED_PLATFORM);
  }

  private static List<Kind> violationKinds(List<SiteDefinition> definitions) {
    InvalidSitesConfiguration failure =
        catchThrowableOfType(
            InvalidSitesConfiguration.class, () -> SitesConfiguration.of(definitions));
    assertThat(failure).as("InvalidSitesConfiguration raised").isNotNull();
    return failure.violations().stream().map(SitesConfigurationViolation::kind).toList();
  }
}
