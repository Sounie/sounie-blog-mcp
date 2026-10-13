package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.adapter.out.ImageCandidateBuilder.anImage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Each image exclusion rule on its own; the composition is tested by the extractor. */
class ImageExclusionTest {

  @Nested
  class Emoji {

    @ParameterizedTest(name = "class=\"{0}\"")
    @ValueSource(strings = {"emoji", "wp-smiley", "alignleft emoji", "wp-smiley size-small"})
    @DisplayName("AC-CAT-34: an emoji or WordPress smiley is excluded")
    void excludes_an_emoji_or_smiley(String classNames) {
      assertThat(ImageExclusion.EMOJI.excludes(anImage().classNames(classNames).build())).isTrue();
    }

    @ParameterizedTest(name = "class=\"{0}\"")
    @ValueSource(strings = {"", "wp-image-42 size-large", "emojis", "not-emoji"})
    void keeps_an_image_without_an_emoji_class(String classNames) {
      assertThat(ImageExclusion.EMOJI.excludes(anImage().classNames(classNames).build())).isFalse();
    }
  }

  @Nested
  class TooSmall {

    @ParameterizedTest(name = "width=\"{0}\" height=\"{1}\"")
    @CsvSource(
        value = {"1, 1", "0, 0", "63, 1000", "1000, 63", "63, ''", "'', 63"},
        emptyValue = "")
    @DisplayName("AC-CAT-34: a declared width or height below 64 is excluded (tracking pixels too)")
    void excludes_a_declared_size_below_64(String width, String height) {
      assertThat(ImageExclusion.TOO_SMALL.excludes(anImage().width(width).height(height).build()))
          .isTrue();
    }

    @ParameterizedTest(name = "width=\"{0}\" height=\"{1}\"")
    @CsvSource(
        value = {"64, 64", "1024, 768", "'', ''", "100%, 100%", "100%, ''", "'', auto"},
        emptyValue = "")
    @DisplayName("AC-CAT-34: 64x64, a non-pixel size, or no size attributes are kept")
    void keeps_a_size_of_at_least_64_or_no_declared_size(String width, String height) {
      assertThat(ImageExclusion.TOO_SMALL.excludes(anImage().width(width).height(height).build()))
          .isFalse();
    }
  }

  @Nested
  class NotHttps {

    @ParameterizedTest(name = "src=\"{0}\"")
    @ValueSource(
        strings = {
          "data:image/png;base64,iVBORw0KGgo=",
          "http://blog2.sounie.nz/a.jpg",
          "https://exa mple.com/x.jpg",
          "ftp://x.example/a.jpg",
          ""
        })
    @DisplayName("AC-CAT-35: data:, http:, unparseable or missing image URLs are excluded")
    void excludes_an_image_whose_resolved_url_is_not_https(String src) {
      assertThat(ImageExclusion.NOT_HTTPS.excludes(anImage().src(src).build())).isTrue();
    }

    @ParameterizedTest(name = "src=\"{0}\"")
    @ValueSource(
        strings = {
          "https://i0.wp.com/blog2.sounie.nz/grapes.png",
          "//i0.wp.com/b.jpg",
          "/wp-content/uploads/c.jpg",
          "d.jpg"
        })
    @DisplayName("AC-CAT-35: https, protocol-relative and relative image URLs are kept")
    void keeps_an_image_whose_resolved_url_is_https(String src) {
      assertThat(ImageExclusion.NOT_HTTPS.excludes(anImage().src(src).build())).isFalse();
    }
  }

  @Test
  @DisplayName("Section 3.6: an ordinary photo is excluded by no rule")
  void no_rule_excludes_an_ordinary_photo() {
    ImageCandidate photo = anImage().width("1024").height("768").classNames("wp-image-7").build();

    assertThat(Arrays.stream(ImageExclusion.values()).filter(rule -> rule.excludes(photo)))
        .isEmpty();
  }
}
