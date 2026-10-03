package nz.sounie.blogmcp.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileKeyTest {

  private static final List<String> AC_IDS = List.of("123", "a/b", "..", "x:y", "ü");

  @ParameterizedTest
  @ValueSource(strings = {"123", "a/b", "..", "x:y", "ü", "tag:blogger.com,1999:blog-1.post-9"})
  @DisplayName("AC-APP-35: a key contains only [A-Za-z0-9_%-]")
  void key_uses_only_safe_characters(String identifier) {
    assertThat(FileKey.of(identifier).value()).matches("[A-Za-z0-9_%-]+");
  }

  @Test
  @DisplayName("AC-APP-35: two different IDs never map to the same key")
  void different_ids_give_different_keys() {
    List<String> ids =
        List.of("123", "a/b", "..", "x:y", "ü", "a%2Fb", "%", "%25", "x%3Ay", "A", "a", ".", "_");

    assertThat(ids.stream().map(id -> FileKey.of(id).value())).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("AC-APP-35: '..' cannot appear as a path segment")
  void a_key_never_escapes_its_directory() {
    Path directory = Path.of("data", "catalog", "posts", "sounie-wp");

    AC_IDS.forEach(
        id -> {
          String key = FileKey.of(id).value();
          assertThat(key).isNotIn(".", "..");
          assertThat(directory.resolve(key + ".json").normalize().getParent()).isEqualTo(directory);
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "abc_DEF-9"})
  void keeps_safe_characters_unchanged(String identifier) {
    assertThat(FileKey.of(identifier).value()).isEqualTo(identifier);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"a/b|a%2Fb", "..|%2E%2E", "x:y|x%3Ay", "ü|%C3%BC", "%|%25", "a b|a%20b"})
  void percent_encodes_the_utf8_bytes_of_other_characters(String identifier, String key) {
    assertThat(FileKey.of(identifier).value()).isEqualTo(key);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a/b", "..", "x.json"})
  void rejects_a_value_that_is_not_an_encoded_key(String value) {
    assertThatThrownBy(() -> new FileKey(value)).isInstanceOf(IllegalArgumentException.class);
  }
}
