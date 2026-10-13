package nz.sounie.blogmcp.search.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import nz.sounie.blogmcp.search.domain.post.MalformedCatalogPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PostImageTest {

  @Test
  @DisplayName("AC-SRCH-66: an absolute https URL with alt text and caption is a post image")
  void parses_a_valid_image() {
    PostImage image = PostImage.of("https://i0.wp.com/a.jpg", "Swan", "");

    assertThat(image.url()).isEqualTo(URI.create("https://i0.wp.com/a.jpg"));
    assertThat(image.altText()).isEqualTo("Swan");
    assertThat(image.caption()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://x.example/a.jpg",
        "/a.jpg",
        "a.jpg",
        "https:///a.jpg",
        "https://x.example/a b.jpg",
        "ftp://x.example/a.jpg"
      })
  @DisplayName("AC-SRCH-66: a URL that is not absolute https with a host is malformed")
  void rejects_a_url_that_is_not_absolute_https(String url) {
    assertThatThrownBy(() -> PostImage.of(url, "", "")).isInstanceOf(MalformedCatalogPost.class);
  }

  @ParameterizedTest(name = "field {0} is null")
  @ValueSource(ints = {0, 1, 2})
  @DisplayName("AC-SRCH-66: a null field is malformed, not a NullPointerException")
  void rejects_a_null_field(int nullField) {
    String[] fields = {"https://x.example/a.jpg", "Alt", "Caption"};
    fields[nullField] = null;

    assertThatThrownBy(() -> PostImage.of(fields[0], fields[1], fields[2]))
        .isInstanceOf(MalformedCatalogPost.class);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '"',
      value = {
        "A black  swan | In reeds | A black swan",
        "\"  \"        | In reeds | In reeds",
        "\"\"          | \"\"     | Image: https://x.example/a.jpg",
        "\"\"          | \"  \"   | Image: https://x.example/a.jpg",
        "\" Swan\t\"   | In reeds | Swan"
      })
  @DisplayName("AC-SRCH-67: the description is the alt text, then the caption, then the URL")
  void description_falls_back_from_alt_text_to_caption_to_url(
      String altText, String caption, String description) {
    assertThat(PostImage.of("https://x.example/a.jpg", altText, caption).description())
        .isEqualTo(description);
  }
}
