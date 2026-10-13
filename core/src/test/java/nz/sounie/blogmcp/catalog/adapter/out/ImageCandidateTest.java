package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.adapter.out.ImageCandidateBuilder.POST_URL;
import static nz.sounie.blogmcp.catalog.adapter.out.ImageCandidateBuilder.anImage;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Reading one {@code <img>} (real jsoup) and resolving its URL against the post's URL. */
class ImageCandidateTest {

  @Nested
  class ResolvingTheUrl {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
      "//i0.wp.com/b.jpg, https://i0.wp.com/b.jpg",
      "/wp-content/uploads/c.jpg, https://blog2.sounie.nz/wp-content/uploads/c.jpg",
      "d.jpg, https://blog2.sounie.nz/2026/09/20/hello/d.jpg",
      "https://i0.wp.com/a.jpg, https://i0.wp.com/a.jpg"
    })
    @DisplayName("AC-CAT-35: the image URL is resolved against the post's canonical URL")
    void resolves_the_url_against_the_canonical_url(String src, String resolved) {
      assertThat(anImage().src(src).build().resolvedUrl()).contains(URI.create(resolved));
    }

    @Test
    @DisplayName("AC-CAT-35 / Q10: an absolute http URL stays http, never upgraded")
    void never_upgrades_an_http_url() {
      assertThat(anImage().src("http://blog2.sounie.nz/a.jpg").build().resolvedUrl())
          .contains(URI.create("http://blog2.sounie.nz/a.jpg"));
    }

    @Test
    @DisplayName("AC-CAT-35: an unparseable URL resolves to nothing, without throwing")
    void an_unparseable_url_resolves_to_nothing() {
      assertThat(anImage().src("https://exa mple.com/x.jpg").build().resolvedUrl()).isEmpty();
    }

    @Test
    @DisplayName("AC-CAT-36: the srcset choice wins over src")
    void prefers_the_srcset_choice_over_src() {
      ImageCandidate candidate =
          anImage()
              .src("https://x.example/s.jpg")
              .srcset("https://x.example/300.jpg 300w, /uploads/1024.jpg 1024w")
              .build();

      assertThat(candidate.resolvedUrl())
          .contains(URI.create("https://blog2.sounie.nz/uploads/1024.jpg"));
    }

    @Test
    @DisplayName("AC-CAT-36: without a qualifying srcset candidate, src is used")
    void falls_back_to_src_when_no_srcset_candidate_qualifies() {
      ImageCandidate candidate =
          anImage()
              .src("https://x.example/s.jpg")
              .srcset("https://x.example/3000.jpg 3000w")
              .build();

      assertThat(candidate.resolvedUrl()).contains(URI.create("https://x.example/s.jpg"));
    }

    @Test
    @DisplayName("AC-CAT-36: no src and no qualifying srcset candidate gives no URL")
    void no_src_and_no_qualifying_srcset_gives_no_url() {
      ImageCandidate candidate = anImage().src("").srcset("https://x.example/2x.jpg 2x").build();

      assertThat(candidate.resolvedUrl()).isEmpty();
    }
  }

  @Nested
  class ReadingAnImgElement {

    private ImageCandidate firstImageIn(String html) {
      Element img = Jsoup.parseBodyFragment(html).selectFirst("img");
      return ImageCandidate.of(img, POST_URL);
    }

    @Test
    void reads_every_attribute_as_written() {
      ImageCandidate candidate =
          firstImageIn(
              "<img src=\"/a.jpg\" srcset=\"/a-300.jpg 300w\" class=\"wp-image-1 emoji\""
                  + " width=\"1\" height=\"100%\">");

      assertThat(candidate.postUrl()).isEqualTo(POST_URL);
      assertThat(candidate.src()).isEqualTo("/a.jpg");
      assertThat(candidate.srcset()).isEqualTo("/a-300.jpg 300w");
      assertThat(candidate.classNames()).isEqualTo("wp-image-1 emoji");
      assertThat(candidate.width()).isEqualTo("1");
      assertThat(candidate.height()).isEqualTo("100%");
    }

    @Test
    void absent_attributes_are_empty() {
      ImageCandidate candidate = firstImageIn("<img>");

      assertThat(candidate.src()).isEmpty();
      assertThat(candidate.srcset()).isEmpty();
      assertThat(candidate.classNames()).isEmpty();
      assertThat(candidate.width()).isEmpty();
      assertThat(candidate.height()).isEmpty();
      assertThat(candidate.altText()).isEmpty();
      assertThat(candidate.caption()).isEmpty();
    }

    @Test
    @DisplayName("AC-CAT-33: alt text has entities decoded and whitespace collapsed")
    void alt_text_has_entities_decoded_and_whitespace_collapsed() {
      assertThat(
              firstImageIn("<img src=\"a.jpg\" alt=\"A black  swan &amp;\n cygnets\">").altText())
          .isEqualTo("A black swan & cygnets");
    }

    @Test
    @DisplayName("AC-CAT-33: the caption is the figcaption text of the enclosing figure")
    void caption_is_the_text_of_the_figcaption_of_the_enclosing_figure() {
      ImageCandidate candidate =
          firstImageIn(
              "<figure class=\"wp-block-image\"><a href=\"#\"><img src=\"a.jpg\"></a>"
                  + "<figcaption>Swan in the <em>reeds</em>,\n  at   dusk</figcaption></figure>");

      assertThat(candidate.caption()).isEqualTo("Swan in the reeds, at dusk");
    }

    @Test
    @DisplayName("Glossary: the caption comes from the nearest enclosing figure")
    void caption_comes_from_the_nearest_enclosing_figure() {
      ImageCandidate candidate =
          firstImageIn(
              "<figure><figure><img src=\"a.jpg\"><figcaption>Inner</figcaption></figure>"
                  + "<figcaption>Outer</figcaption></figure>");

      assertThat(candidate.caption()).isEqualTo("Inner");
    }

    @Test
    @DisplayName("AC-CAT-33: an image outside a figure has an empty caption")
    void image_outside_a_figure_has_an_empty_caption() {
      ImageCandidate candidate =
          firstImageIn(
              "<p><img src=\"a.jpg\"></p><figure><img src=\"b.jpg\">"
                  + "<figcaption>Not mine</figcaption></figure>");

      assertThat(candidate.caption()).isEmpty();
    }
  }
}
