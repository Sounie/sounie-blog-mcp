package nz.sounie.blogmcp.search.domain.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.sounie.blogmcp.search.domain.image.PostImage;
import nz.sounie.blogmcp.search.domain.text.WordSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

  @Nested
  class SettledImages {

    private final PostImage swan = PostImage.of("https://x.example/a.jpg", "Swan", "");
    private final PostImage grapes = PostImage.of("https://x.example/b.png", "", "Grapes");

    private ContentFingerprint withImages(PostImage... images) {
      return ContentFingerprint.of(RECIPE, "Hello", WordSequence.of("a b"), List.of(images));
    }

    @Test
    @DisplayName("AC-SRCH-68: with no images the fingerprint is exactly the text-only one")
    void no_images_is_the_text_only_fingerprint() {
      assertThat(withImages()).isEqualTo(fingerprint("Hello", "a b"));
    }

    @Test
    @DisplayName("AC-SRCH-70: adding an image changes the fingerprint")
    void an_added_image_changes_the_fingerprint() {
      assertThat(withImages(swan, grapes)).isNotEqualTo(withImages(swan));
    }

    @Test
    @DisplayName("AC-SRCH-70: an image's URL is part of the fingerprint")
    void an_image_url_change_changes_the_fingerprint() {
      assertThat(withImages(PostImage.of("https://x.example/c.jpg", "Swan", "")))
          .isNotEqualTo(withImages(swan));
    }

    @Test
    @DisplayName("AC-SRCH-70: an image's description is part of the fingerprint")
    void an_image_description_change_changes_the_fingerprint() {
      assertThat(withImages(PostImage.of("https://x.example/a.jpg", "Black swan", "")))
          .isNotEqualTo(withImages(swan));
    }

    @Test
    void image_order_is_part_of_the_fingerprint() {
      assertThat(withImages(grapes, swan)).isNotEqualTo(withImages(swan, grapes));
    }

    @Test
    void the_same_description_from_alt_text_or_caption_is_the_same_fingerprint() {
      assertThat(withImages(PostImage.of("https://x.example/a.jpg", "", "Swan")))
          .isEqualTo(withImages(swan));
    }
  }

  @Test
  @DisplayName("AC-SRCH-29: a recipe change changes the fingerprint")
  void a_recipe_change_changes_the_fingerprint() {
    assertThat(ContentFingerprint.of(new IndexRecipe("other"), "Hello", WordSequence.of("a b")))
        .isNotEqualTo(fingerprint("Hello", "a b"));
  }
}
