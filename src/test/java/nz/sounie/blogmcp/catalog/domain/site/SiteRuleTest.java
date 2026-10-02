package nz.sounie.blogmcp.catalog.domain.site;

import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.BASE_URL_HAS_QUERY_OR_FRAGMENT;
import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.BASE_URL_NOT_ABSOLUTE_HTTPS;
import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.INVALID_SITE_ID;
import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.UNSUPPORTED_PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SiteRuleTest {

  private static final SiteDefinition VALID =
      new SiteDefinition("sounie-wp", "WORDPRESS", "https://blog2.sounie.nz");

  private static SiteDefinition withId(String id) {
    return new SiteDefinition(id, VALID.platform(), VALID.baseUrl());
  }

  private static SiteDefinition withPlatform(String platform) {
    return new SiteDefinition(VALID.id(), platform, VALID.baseUrl());
  }

  private static SiteDefinition withBaseUrl(String baseUrl) {
    return new SiteDefinition(VALID.id(), VALID.platform(), baseUrl);
  }

  @Test
  void a_valid_definition_breaks_no_rule() {
    for (SiteRule rule : SiteRule.values()) {
      assertThat(rule.check(VALID)).as(rule.name()).isEmpty();
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"Blog", "my_blog", "", "01234567890123456789012345678901234567890"})
  void site_id_rule_rejects_anything_but_a_slug(String id) {
    assertThat(SiteRule.SITE_ID.check(withId(id)))
        .hasValueSatisfying(violation -> assertThat(violation.kind()).isEqualTo(INVALID_SITE_ID));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"ghost", "", "word press"})
  void platform_rule_rejects_unknown_or_missing_platforms(String platform) {
    assertThat(SiteRule.SUPPORTED_PLATFORM.check(withPlatform(platform)))
        .hasValueSatisfying(
            violation -> assertThat(violation.kind()).isEqualTo(UNSUPPORTED_PLATFORM));
  }

  @Test
  void platform_rule_accepts_a_platform_name_in_any_case() {
    assertThat(SiteRule.SUPPORTED_PLATFORM.check(withPlatform("blogger"))).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"http://a.example", "a.example", "/blog", "https://", "not a url"})
  void base_url_rule_rejects_anything_but_absolute_https_with_a_host(String baseUrl) {
    assertThat(SiteRule.HTTPS_BASE_URL.check(withBaseUrl(baseUrl)))
        .hasValueSatisfying(
            violation -> assertThat(violation.kind()).isEqualTo(BASE_URL_NOT_ABSOLUTE_HTTPS));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://a.example/?blog=1", "https://a.example/#top"})
  void base_url_rule_rejects_a_query_or_fragment(String baseUrl) {
    assertThat(SiteRule.HTTPS_BASE_URL.check(withBaseUrl(baseUrl)))
        .hasValueSatisfying(
            violation -> assertThat(violation.kind()).isEqualTo(BASE_URL_HAS_QUERY_OR_FRAGMENT));
  }

  @Test
  void a_violation_names_the_offending_value() {
    assertThat(SiteRule.SUPPORTED_PLATFORM.check(withPlatform("ghost")))
        .hasValueSatisfying(violation -> assertThat(violation.detail()).contains("ghost"));
  }
}
