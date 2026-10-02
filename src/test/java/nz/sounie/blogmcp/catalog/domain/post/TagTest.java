package nz.sounie.blogmcp.catalog.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TagTest {

  @Test
  void is_trimmed() {
    assertThat(new Tag("  Java ").value()).isEqualTo("Java");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t\n"})
  void must_not_be_blank(String value) {
    assertThatThrownBy(() -> new Tag(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tags_are_equal_ignoring_case() {
    assertThat(new Tag("Java")).isEqualTo(new Tag("java")).hasSameHashCodeAs(new Tag("JAVA"));
  }

  @Test
  void tags_with_different_text_are_different() {
    assertThat(new Tag("Java")).isNotEqualTo(new Tag("Jakarta"));
  }

  @Test
  @DisplayName("AC-CAT-9: labels are trimmed and de-duplicated ignoring case, first casing kept")
  void tag_set_keeps_the_first_casing_of_case_insensitive_duplicates() {
    Set<Tag> tags = Tag.setOf(List.of("DDD", " ddd ", "Java"));

    assertThat(tags).extracting(Tag::value).containsExactly("DDD", "Java");
  }

  @Test
  @DisplayName("AC-CAT-8: java and Java collapse to the first seen")
  void tag_set_collapses_java_and_Java() {
    assertThat(Tag.setOf(List.of("java", "Java"))).extracting(Tag::value).containsExactly("java");
  }

  @Test
  void tag_set_drops_blank_labels() {
    assertThat(Tag.setOf(List.of(" ", "DDD", ""))).extracting(Tag::value).containsExactly("DDD");
  }

  @Test
  void tag_set_may_be_empty() {
    assertThat(Tag.setOf(List.of())).isEmpty();
  }

  @Test
  void tag_set_is_unmodifiable() {
    Set<Tag> tags = Tag.setOf(List.of("DDD"));

    assertThatThrownBy(() -> tags.add(new Tag("Java")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
