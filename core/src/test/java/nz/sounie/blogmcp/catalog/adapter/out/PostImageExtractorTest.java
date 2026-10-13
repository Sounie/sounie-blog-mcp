package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.adapter.out.ImageCandidateBuilder.POST_URL;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import nz.sounie.blogmcp.catalog.domain.post.PostImage;
import nz.sounie.blogmcp.catalog.domain.post.PostImages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Image extraction with real jsoup: the composition of candidate reading, the srcset choice, the
 * exclusion rule table and {@link PostImages}. Each rule's edge cases are tested on its own type.
 */
class PostImageExtractorTest {

  /** The content HTML of AC-CAT-33. */
  static final String AC_CAT_33_HTML =
      "<p>Intro</p><figure><img src=\"https://blog2.sounie.nz/wp-content/uploads/swan.jpg\""
          + " alt=\"A black  swan\"><figcaption>Swan in the <em>reeds</em></figcaption></figure>"
          + "<p>More</p><img src=\"https://i0.wp.com/blog2.sounie.nz/grapes.png\">";

  /** The content HTML of AC-CAT-34: an emoji, a tracking pixel and a photo. */
  static final String AC_CAT_34_HTML =
      "<p>Hello <img class=\"emoji\""
          + " src=\"https://s.w.org/images/core/emoji/15.0.3/72x72/1f600.png\"></p>"
          + "<img src=\"https://pixel.wp.com/g.gif\" width=\"1\" height=\"1\">"
          + "<img src=\"https://blog2.sounie.nz/wp-content/uploads/swan.jpg\" width=\"1024\""
          + " height=\"768\">";

  private final PostImageExtractor extractor = new PostImageExtractor();

  private static PostImage image(String url, String altText, String caption) {
    return new PostImage(URI.create(url), altText, caption);
  }

  private static PostImage image(String url) {
    return image(url, "", "");
  }

  @Test
  @DisplayName("AC-CAT-33: images are recorded in document order with alt text and caption")
  void records_images_in_document_order_with_alt_text_and_caption() {
    PostImages images = extractor.extract(AC_CAT_33_HTML, POST_URL);

    assertThat(images.asList())
        .containsExactly(
            image(
                "https://blog2.sounie.nz/wp-content/uploads/swan.jpg",
                "A black swan",
                "Swan in the reeds"),
            image("https://i0.wp.com/blog2.sounie.nz/grapes.png"));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", "<p>No pictures here.</p>", "<figure><figcaption>x</figcaption>"})
  @DisplayName("AC-CAT-33: a body with no <img>, or an empty body, gives no images")
  void a_body_without_images_gives_none(String html) {
    assertThat(extractor.extract(html, POST_URL)).isEqualTo(PostImages.none());
  }

  @Test
  @DisplayName("AC-CAT-34: an emoji and a tracking pixel are excluded; the photo is kept")
  void excludes_emoji_and_tracking_pixel_and_keeps_the_photo() {
    assertThat(extractor.extract(AC_CAT_34_HTML, POST_URL).asList())
        .containsExactly(image("https://blog2.sounie.nz/wp-content/uploads/swan.jpg"));
  }

  @Test
  @DisplayName("AC-CAT-35: only https URLs are kept, after resolving against the canonical URL")
  void keeps_only_https_urls_after_resolving_against_the_canonical_url() {
    String html =
        "<img src=\"data:image/png;base64,iVBORw0KGgo=\">"
            + "<img src=\"http://blog2.sounie.nz/a.jpg\">"
            + "<img src=\"//i0.wp.com/b.jpg\">"
            + "<img src=\"/wp-content/uploads/c.jpg\">"
            + "<img src=\"https://exa mple.com/x.jpg\">"
            + "<img src=\"d.jpg\">";

    assertThat(extractor.extract(html, POST_URL).asList())
        .containsExactly(
            image("https://i0.wp.com/b.jpg"),
            image("https://blog2.sounie.nz/wp-content/uploads/c.jpg"),
            image("https://blog2.sounie.nz/2026/09/20/hello/d.jpg"));
  }

  @Test
  @DisplayName("AC-CAT-36: srcset contributes its largest candidate up to 2048 px, else src")
  void uses_the_srcset_choice_and_falls_back_to_src() {
    String html =
        "<img src=\"https://x.example/s.jpg\" srcset=\"https://x.example/300.jpg 300w,"
            + " https://x.example/2048.jpg 2048w, https://x.example/1024.jpg 1024w,"
            + " https://x.example/4096.jpg 4096w\">"
            + "<img src=\"https://x.example/t.jpg\" srcset=\"https://x.example/3000.jpg 3000w\">"
            + "<img srcset=\"https://x.example/2x.jpg 2x\">";

    assertThat(extractor.extract(html, POST_URL).asList())
        .containsExactly(image("https://x.example/2048.jpg"), image("https://x.example/t.jpg"));
  }

  @Test
  @DisplayName("AC-CAT-37: an image URL is recorded once, keeping the first occurrence")
  void records_an_image_url_once() {
    String html =
        "<img src=\"https://x.example/a.jpg\" alt=\"First\">"
            + "<img src=\"https://x.example/b.jpg\">"
            + "<img src=\"https://x.example/a.jpg\" alt=\"Second\">";

    assertThat(extractor.extract(html, POST_URL).asList())
        .containsExactly(
            image("https://x.example/a.jpg", "First", ""), image("https://x.example/b.jpg"));
  }
}
