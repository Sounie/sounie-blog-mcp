package nz.sounie.blogmcp.catalog.domain.post;

import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CanonicalUrlTest {

  @Test
  void accepts_an_https_link_on_the_site_host() {
    CanonicalUrl url = CanonicalUrl.onSite(SOUNIE_WP, "https://blog2.sounie.nz/a/");

    assertThat(url.value()).isEqualTo(URI.create("https://blog2.sounie.nz/a/"));
  }

  @Test
  void compares_the_host_ignoring_case() {
    CanonicalUrl url = CanonicalUrl.onSite(SOUNIE_WP, "https://BLOG2.Sounie.NZ/a/");

    assertThat(url.isOn(SOUNIE_WP)).isTrue();
  }

  @Test
  @DisplayName("AC-CAT-16: an http link is never upgraded to https")
  void rejects_an_http_link_instead_of_upgrading_it() {
    assertThatThrownBy(() -> CanonicalUrl.onSite(SOUNIE_WP, "http://blog2.sounie.nz/x"))
        .isInstanceOf(CanonicalUrlNotOnSite.class);
  }

  @Test
  @DisplayName("AC-CAT-16: a link on another host is not on the site")
  void rejects_a_link_on_another_host() {
    assertThatThrownBy(() -> CanonicalUrl.onSite(SOUNIE_WP, "https://evil.example/x"))
        .isInstanceOf(CanonicalUrlNotOnSite.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/a/", "blog2.sounie.nz/a/", "::not a url::", ""})
  void rejects_links_that_are_not_absolute_urls(String link) {
    assertThatThrownBy(() -> CanonicalUrl.onSite(SOUNIE_WP, link))
        .isInstanceOf(CanonicalUrlNotOnSite.class);
  }

  @Test
  void is_not_on_a_site_with_a_different_host() {
    CanonicalUrl url = new CanonicalUrl(URI.create("https://blog.elegant-solutions.london/a.html"));

    assertThat(url.isOn(SOUNIE_WP)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://blog2.sounie.nz/a/", "/relative/", "mailto:someone@example.com"})
  void must_be_absolute_https(String value) {
    assertThatThrownBy(() -> new CanonicalUrl(URI.create(value)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "https://blog2.sounie.nz/2026/09/20/hello/, https://blog2.sounie.nz/2026/09/20/hello",
    "HTTPS://BLOG2.Sounie.nz/2026/09/20/hello, https://blog2.sounie.nz/2026/09/20/hello",
    "https://blog2.sounie.nz:443/2026/09/20/hello/, https://blog2.sounie.nz/2026/09/20/hello",
    "https://blog2.sounie.nz/2026/09/20/hello/#comments, https://blog2.sounie.nz/2026/09/20/hello",
    "https://blog2.sounie.nz:8443/a/, https://blog2.sounie.nz:8443/a",
    "https://blog2.sounie.nz/Path/Case/, https://blog2.sounie.nz/Path/Case"
  })
  void normalised_form_lowers_scheme_and_host_and_drops_default_port_fragment_and_trailing_slash(
      String url, String expected) {
    assertThat(new CanonicalUrl(URI.create(url)).normalisedForm()).isEqualTo(expected);
  }

  @Test
  void normalised_form_keeps_the_query_string() {
    assertThat(
            new CanonicalUrl(URI.create("https://blog2.sounie.nz/a/?replytocom=5"))
                .normalisedForm())
        .isEqualTo("https://blog2.sounie.nz/a?replytocom=5");
  }
}
