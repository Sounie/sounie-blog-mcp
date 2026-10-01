package nz.sounie.blogmcp.catalog.domain;

import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.DUPLICATE_BASE_URL;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.DUPLICATE_SITE_ID;
import static nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind.NO_SITES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationRuleTest {

  private static SiteDefinition site(String id, String baseUrl) {
    return new SiteDefinition(id, "WORDPRESS", baseUrl);
  }

  @Test
  void at_least_one_site_rejects_an_empty_list() {
    assertThat(ConfigurationRule.AT_LEAST_ONE_SITE.check(List.of()))
        .extracting(SitesConfigurationViolation::kind)
        .containsExactly(NO_SITES);
  }

  @Test
  void at_least_one_site_accepts_one_site() {
    assertThat(ConfigurationRule.AT_LEAST_ONE_SITE.check(List.of(site("a", "https://a.example"))))
        .isEmpty();
  }

  @Test
  void a_repeated_site_id_is_reported_once_per_repeat() {
    List<SiteDefinition> definitions =
        List.of(
            site("blog", "https://a.example"),
            site("blog", "https://b.example"),
            site("blog", "https://c.example"),
            site("other", "https://d.example"));

    assertThat(ConfigurationRule.UNIQUE_SITE_IDS.check(definitions))
        .extracting(SitesConfigurationViolation::kind)
        .containsExactly(DUPLICATE_SITE_ID, DUPLICATE_SITE_ID);
  }

  @Test
  void repeated_invalid_site_ids_are_left_to_the_site_id_rule() {
    List<SiteDefinition> definitions =
        List.of(site("Bad Id", "https://a.example"), site("Bad Id", "https://b.example"));

    assertThat(ConfigurationRule.UNIQUE_SITE_IDS.check(definitions)).isEmpty();
  }

  @Test
  void base_urls_differing_only_in_host_case_or_trailing_slash_are_the_same_site() {
    List<SiteDefinition> definitions =
        List.of(site("one", "https://blog2.sounie.nz"), site("two", "https://BLOG2.sounie.nz/"));

    assertThat(ConfigurationRule.UNIQUE_BASE_URLS.check(definitions))
        .extracting(SitesConfigurationViolation::kind)
        .containsExactly(DUPLICATE_BASE_URL);
  }

  @Test
  void base_urls_on_different_paths_or_ports_are_different_sites() {
    List<SiteDefinition> definitions =
        List.of(
            site("one", "https://example.org/a"),
            site("two", "https://example.org/b"),
            site("three", "https://example.org:8443/a"));

    assertThat(ConfigurationRule.UNIQUE_BASE_URLS.check(definitions)).isEmpty();
  }

  @Test
  void repeated_invalid_base_urls_are_left_to_the_base_url_rule() {
    List<SiteDefinition> definitions =
        List.of(site("one", "http://a.example"), site("two", "http://a.example"));

    assertThat(ConfigurationRule.UNIQUE_BASE_URLS.check(definitions)).isEmpty();
  }
}
