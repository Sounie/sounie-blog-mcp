package nz.sounie.blogmcp.catalog.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Section 3.6: what a valid post image is. */
class PostImageTest {

  private static final URI PHOTO = URI.create("https://i0.wp.com/blog2.sounie.nz/grapes.png");

  @Test
  @DisplayName(
      "Invariant 7: an image URL need not be on the site host; alt and caption are stripped")
  void keeps_an_https_url_on_any_host_and_strips_alt_text_and_caption() {
    PostImage image = new PostImage(PHOTO, "  A black swan \n", "\tSwan in the reeds  ");

    assertThat(image.url()).isEqualTo(PHOTO);
    assertThat(image.altText()).isEqualTo("A black swan");
    assertThat(image.caption()).isEqualTo("Swan in the reeds");
  }

  @Test
  void blank_alt_text_and_caption_mean_none_and_become_empty() {
    PostImage image = new PostImage(PHOTO, "   ", "\n");

    assertThat(image.altText()).isEmpty();
    assertThat(image.caption()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://blog2.sounie.nz/a.jpg",
        "data:image/png;base64,iVBOR",
        "/wp-content/uploads/c.jpg",
        "d.jpg",
        "https:/no-host.jpg"
      })
  @DisplayName(
      "Invariant 7: an image URL that is not absolute https with a host is a broken invariant")
  void rejects_a_url_that_is_not_absolute_https_with_a_host(String url) {
    URI uri = URI.create(url);

    assertThatThrownBy(() -> new PostImage(uri, "", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_a_null_url() {
    assertThatThrownBy(() -> new PostImage(null, "", "")).isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_null_alt_text() {
    assertThatThrownBy(() -> new PostImage(PHOTO, null, ""))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_a_null_caption() {
    assertThatThrownBy(() -> new PostImage(PHOTO, "", null))
        .isInstanceOf(NullPointerException.class);
  }
}
