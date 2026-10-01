package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentFingerprintTest {

  private static final IndexRecipe RECIPE = new IndexRecipe("model/w300-o50-t400-tt64-c64/p1");

  private static ContentFingerprint fingerprint(String title, String body) {
    return ContentFingerprint.of(RECIPE, title, WordSequence.of(body));
  }

  @Test
  @DisplayName("AC-SRCH-6: whitespace-only edits give equal fingerprints")
  void whitespace_only_edits_do_not_change_the_fingerprint() {
    assertThat(fingerprint(" Hello  World", "a\tb c\n"))
        .isEqualTo(fingerprint("Hello World", "a b\n\nc"));
  }

  @Test
  void is_lower_case_hex_sha_256() {
    assertThat(fingerprint("Hello", "a b").value()).matches("[0-9a-f]{64}");
  }

  @Test
  void a_body_change_changes_the_fingerprint() {
    assertThat(fingerprint("Hello", "a b")).isNotEqualTo(fingerprint("Hello", "a c"));
  }

  @Test
  void joining_words_changes_the_fingerprint() {
    assertThat(fingerprint("Hello", "a b")).isNotEqualTo(fingerprint("Hello", "ab"));
  }

  @Test
  void a_title_change_changes_the_fingerprint() {
    assertThat(fingerprint("Hello", "a b")).isNotEqualTo(fingerprint("Goodbye", "a b"));
  }

  @Test
  void moving_a_word_between_title_and_body_changes_the_fingerprint() {
    assertThat(fingerprint("a", "b c")).isNotEqualTo(fingerprint("a b", "c"));
  }

  @Test
  @DisplayName("AC-SRCH-29: a recipe change changes the fingerprint")
  void a_recipe_change_changes_the_fingerprint() {
    assertThat(ContentFingerprint.of(new IndexRecipe("other"), "Hello", WordSequence.of("a b")))
        .isNotEqualTo(fingerprint("Hello", "a b"));
  }
}
