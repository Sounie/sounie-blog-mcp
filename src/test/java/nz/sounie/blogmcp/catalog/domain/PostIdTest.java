package nz.sounie.blogmcp.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostIdTest {

  private static final PostId SOUNIE_WP_123 =
      new PostId(new SiteId("sounie-wp"), new SourcePostId("123"));

  @Test
  void external_form_is_site_id_colon_source_post_id() {
    assertThat(SOUNIE_WP_123.external()).isEqualTo("sounie-wp:123");
  }

  @Test
  void parses_its_external_form() {
    assertThat(PostId.parse("sounie-wp:123")).isEqualTo(SOUNIE_WP_123);
  }

  @Test
  void parses_blogger_style_long_source_ids() {
    assertThat(PostId.parse("elegant:371460637286630063"))
        .isEqualTo(new PostId(new SiteId("elegant"), new SourcePostId("371460637286630063")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-id", ":123", "sounie-wp:", "Sounie WP:123", ""})
  void rejects_text_that_is_not_an_external_post_id(String text) {
    assertThatThrownBy(() -> PostId.parse(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void source_post_id_must_not_be_blank(String value) {
    assertThatThrownBy(() -> new SourcePostId(value)).isInstanceOf(IllegalArgumentException.class);
  }
}
