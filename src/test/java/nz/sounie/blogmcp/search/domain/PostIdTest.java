package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostIdTest {

  @Test
  void parses_the_external_form() {
    assertThat(PostId.parse("sounie-wp:123")).isEqualTo(new PostId(new SiteId("sounie-wp"), "123"));
  }

  @Test
  void source_post_id_may_contain_colons() {
    assertThat(PostId.parse("elegant:tag:blogger.com,1999:post-9").sourcePostId())
        .isEqualTo("tag:blogger.com,1999:post-9");
  }

  @Test
  void external_form_round_trips() {
    assertThat(new PostId(new SiteId("elegant"), "42").external()).isEqualTo("elegant:42");
  }

  @ParameterizedTest
  @ValueSource(strings = {"no-separator", "sounie-wp:", ":123", "Sounie:1"})
  void rejects_text_that_is_not_a_post_id(String text) {
    assertThatThrownBy(() -> PostId.parse(text)).isInstanceOf(MalformedCatalogPost.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rejects_a_blank_source_post_id(String sourcePostId) {
    assertThatThrownBy(() -> new PostId(new SiteId("sounie-wp"), sourcePostId))
        .isInstanceOf(MalformedCatalogPost.class);
  }
}
