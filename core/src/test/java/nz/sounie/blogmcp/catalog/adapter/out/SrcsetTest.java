package nz.sounie.blogmcp.catalog.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Srcset choice: the largest {@code w} candidate of at most 2048, the first on a tie. */
class SrcsetTest {

  @Test
  @DisplayName("AC-CAT-36: the largest width descriptor up to 2048 is chosen")
  void chooses_the_largest_width_candidate_up_to_2048() {
    Srcset srcset =
        new Srcset(
            "https://x.example/300.jpg 300w, https://x.example/2048.jpg 2048w,"
                + " https://x.example/1024.jpg 1024w, https://x.example/4096.jpg 4096w");

    assertThat(srcset.chosenUrl()).contains("https://x.example/2048.jpg");
  }

  @Test
  void chooses_the_largest_when_every_candidate_is_below_the_limit() {
    Srcset srcset = new Srcset("https://x.example/300.jpg 300w, https://x.example/768.jpg 768w");

    assertThat(srcset.chosenUrl()).contains("https://x.example/768.jpg");
  }

  @Test
  void chooses_the_first_candidate_on_a_tie() {
    Srcset srcset =
        new Srcset("https://x.example/first.jpg 1024w, https://x.example/second.jpg 1024w");

    assertThat(srcset.chosenUrl()).contains("https://x.example/first.jpg");
  }

  @Test
  void keeps_a_relative_candidate_url_as_written() {
    assertThat(new Srcset("/wp-content/uploads/a-1024.jpg 1024w").chosenUrl())
        .contains("/wp-content/uploads/a-1024.jpg");
  }

  @Test
  void reads_candidates_separated_by_newlines_and_extra_spaces() {
    Srcset srcset =
        new Srcset("\n  https://x.example/300.jpg   300w ,\n  https://x.example/600.jpg 600w  \n");

    assertThat(srcset.chosenUrl()).contains("https://x.example/600.jpg");
  }

  @Test
  @DisplayName("HTML spec: a candidate URL is a run of non-whitespace and may contain commas")
  void keeps_a_comma_inside_a_candidate_url() {
    Srcset srcset =
        new Srcset(
            "https://res.cloudinary.com/d/image/upload/w_300,c_fill/a.jpg 300w,"
                + " https://res.cloudinary.com/d/image/upload/w_600,c_fill/a.jpg 600w");

    assertThat(srcset.chosenUrl())
        .contains("https://res.cloudinary.com/d/image/upload/w_600,c_fill/a.jpg");
  }

  @Test
  void ignores_a_very_long_candidate_without_a_width_descriptor_quickly() {
    Srcset srcset = new Srcset("data:image/png;base64," + "A".repeat(100_000));

    assertTimeoutPreemptively(
        Duration.ofSeconds(1), () -> assertThat(srcset.chosenUrl()).isEmpty());
  }

  @Test
  void skips_an_x_candidate_before_a_comma_without_a_space() {
    assertThat(new Srcset("a.jpg 1x,b.jpg 300w").chosenUrl()).contains("b.jpg");
  }

  @Test
  void skips_a_candidate_with_an_unparseable_descriptor() {
    assertThat(new Srcset("a.jpg 300w 200h,b.jpg 600w").chosenUrl()).contains("b.jpg");
  }

  @Test
  void reads_candidates_separated_by_a_comma_without_a_space() {
    assertThat(new Srcset("a.jpg 300w,b.jpg 600w").chosenUrl()).contains("b.jpg");
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        "https://x.example/3000.jpg 3000w",
        "https://x.example/1x.jpg 1x, https://x.example/2x.jpg 2x",
        "",
        "   ",
        "https://x.example/plain.jpg",
        "https://x.example/odd.jpg widew, https://x.example/neg.jpg -5w",
        "https://x.example/frac.jpg 100.5w"
      })
  @DisplayName("AC-CAT-36: no candidate qualifies, so the caller falls back to src")
  void chooses_nothing_when_no_candidate_qualifies(String attribute) {
    assertThat(new Srcset(attribute).chosenUrl()).isEmpty();
  }

  @Test
  void ignores_unqualified_candidates_among_qualifying_ones() {
    Srcset srcset =
        new Srcset(
            "https://x.example/2x.jpg 2x, https://x.example/plain.jpg,"
                + " https://x.example/500.jpg 500w, https://x.example/5000.jpg 5000w");

    assertThat(srcset.chosenUrl()).contains("https://x.example/500.jpg");
  }
}
