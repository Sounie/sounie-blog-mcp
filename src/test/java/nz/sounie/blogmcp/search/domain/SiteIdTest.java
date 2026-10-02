package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SiteIdTest {

  @ParameterizedTest
  @ValueSource(strings = {"a", "sounie-wp", "elegant", "site-2"})
  void accepts_lower_case_letters_digits_and_hyphens(String value) {
    assertThatCode(() -> new SiteId(value)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Sounie", "sounie_wp", "sounie wp", "sounie:wp"})
  void rejects_other_characters_and_empty(String value) {
    assertThatThrownBy(() -> new SiteId(value)).isInstanceOf(MalformedCatalogPost.class);
  }

  @org.junit.jupiter.api.Test
  void accepts_40_characters_and_rejects_41() {
    assertThatCode(() -> new SiteId("a".repeat(40))).doesNotThrowAnyException();
    assertThatThrownBy(() -> new SiteId("a".repeat(41))).isInstanceOf(MalformedCatalogPost.class);
  }
}
