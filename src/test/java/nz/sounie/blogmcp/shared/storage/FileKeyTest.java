package nz.sounie.blogmcp.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-APP-35. Keys are safe on case-insensitive file systems (macOS APFS): fully lower case. */
class FileKeyTest {

  private static final List<String> AC_IDS = List.of("123", "a/b", "..", "x:y", "ü");

  @ParameterizedTest
  @ValueSource(
      strings = {"123", "a/b", "..", "x:y", "ü", "tag:blogger.com,1999:blog-1.post-9", "MixedCase"})
  @DisplayName(
      "AC-APP-35: a key contains only [a-z0-9_%-]; keys are safe on case-insensitive file systems"
          + " (macOS APFS)")
  void key_uses_only_lower_case_safe_characters(String identifier) {
    assertThat(FileKey.of(identifier).value()).matches("[a-z0-9_%-]+");
  }

  @Test
  @DisplayName(
      "AC-APP-35: different IDs never map to keys that are equal, even ignoring case"
          + " (case-insensitive file systems, macOS APFS)")
  void different_ids_give_keys_that_differ_ignoring_case() {
    List<String> ids =
        List.of(
            "123", "a/b", "..", "x:y", "ü", "a%2Fb", "%", "%25", "x%3Ay", "A", "a", ".", "_", "Ab",
            "aB", "AB", "ab", "Ü", "%41", "%2f");

    List<String> folded =
        ids.stream().map(id -> FileKey.of(id).value().toLowerCase(Locale.ROOT)).toList();

    assertThat(folded).doesNotHaveDuplicates();
    for (int i = 0; i < ids.size(); i++) {
      for (int j = i + 1; j < ids.size(); j++) {
        assertThat(FileKey.of(ids.get(i)).value())
            .as("keys of '%s' and '%s'", ids.get(i), ids.get(j))
            .isNotEqualToIgnoringCase(FileKey.of(ids.get(j)).value());
      }
    }
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
  @ValueSource(strings = {"123", "abc_def-9", "a"})
  void keeps_lower_case_letters_digits_underscore_and_hyphen_unchanged(String identifier) {
    assertThat(FileKey.of(identifier).value()).isEqualTo(identifier);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "A|%41",
        "Ab|%41b",
        "a/b|a%2fb",
        "..|%2e%2e",
        "x:y|x%3ay",
        "ü|%c3%bc",
        "Ü|%c3%9c",
        "%|%25",
        "a b|a%20b"
      })
  @DisplayName("AC-APP-35: upper case and other characters become lower-case-hex UTF-8 escapes")
  void percent_encodes_with_lower_case_hex(String identifier, String key) {
    assertThat(FileKey.of(identifier).value()).isEqualTo(key);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a/b", "..", "x.json", "Abc", "a%2Fb", "%C3%BC"})
  void rejects_a_value_that_is_not_a_lower_case_encoded_key(String value) {
    assertThatThrownBy(() -> new FileKey(value)).isInstanceOf(IllegalArgumentException.class);
  }
}
