package nz.sounie.blogmcp.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SiteIdTest {

  @ParameterizedTest
  @ValueSource(strings = {"sounie-wp", "elegant", "a", "0123456789-0123456789-0123456789-012345"})
  void accepts_lower_case_slugs_of_up_to_40_characters(String value) {
    assertThat(new SiteId(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "Sounie-wp",
        "sounie_wp",
        "sounie wp",
        "sounie:wp",
        "01234567890123456789012345678901234567890"
      })
  void rejects_anything_that_is_not_a_lower_case_slug_of_up_to_40_characters(String value) {
    assertThatThrownBy(() -> new SiteId(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> new SiteId(null))
        .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
  }
}
