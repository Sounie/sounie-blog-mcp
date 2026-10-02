package nz.sounie.blogmcp.catalog.domain.site;

import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.BASE_URL_HAS_QUERY_OR_FRAGMENT;
import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.BASE_URL_NOT_ABSOLUTE_HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SiteTest {

  @Test
  void site_host_is_the_host_of_the_base_url() {
    assertThat(TestSites.SOUNIE_WP.host()).isEqualTo("blog2.sounie.nz");
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://blog2.sounie.nz", "https://blog2.sounie.nz/?q=1", "/relative"})
  void a_site_cannot_be_created_with_an_invalid_base_url(String baseUrl) {
    assertThatThrownBy(
            () -> new Site(TestSites.SOUNIE_WP_ID, Platform.WORDPRESS, URI.create(baseUrl)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void base_url_text_that_is_not_a_url_is_not_https() {
    assertThat(Site.baseUrlProblem("not a url")).contains(BASE_URL_NOT_ABSOLUTE_HTTPS);
    assertThat(Site.baseUrlProblem((String) null)).contains(BASE_URL_NOT_ABSOLUTE_HTTPS);
  }

  @Test
  void base_url_problems_are_told_apart() {
    assertThat(Site.baseUrlProblem("http://a.example")).contains(BASE_URL_NOT_ABSOLUTE_HTTPS);
    assertThat(Site.baseUrlProblem("https://a.example#top"))
        .contains(BASE_URL_HAS_QUERY_OR_FRAGMENT);
    assertThat(Site.baseUrlProblem("https://a.example/blog")).isEmpty();
  }

  @Test
  void a_valid_base_url_is_parsed_and_an_invalid_one_is_not() {
    assertThat(Site.validBaseUrl("https://a.example/blog"))
        .contains(URI.create("https://a.example/blog"));
    assertThat(Site.validBaseUrl("http://a.example")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "https://BLOG2.sounie.nz/, https://blog2.sounie.nz",
    "https://blog2.sounie.nz/blog/, https://blog2.sounie.nz/blog"
  })
  void comparable_base_url_ignores_host_case_and_a_trailing_slash(String one, String other) {
    assertThat(Site.comparableBaseUrl(URI.create(one)))
        .isEqualTo(Site.comparableBaseUrl(URI.create(other)));
  }

  @ParameterizedTest
  @CsvSource({
    "https://blog2.sounie.nz/a, https://blog2.sounie.nz/b",
    "https://blog2.sounie.nz/a, https://blog2.sounie.nz:8443/a",
    "https://blog2.sounie.nz/a, https://blog.sounie.nz/a"
  })
  void comparable_base_url_distinguishes_path_port_and_host(String one, String other) {
    assertThat(Site.comparableBaseUrl(URI.create(one)))
        .isNotEqualTo(Site.comparableBaseUrl(URI.create(other)));
  }
}
