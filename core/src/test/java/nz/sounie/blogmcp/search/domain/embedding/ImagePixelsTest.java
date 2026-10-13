package nz.sounie.blogmcp.search.domain.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nz.sounie.blogmcp.search.domain.embedding.InvalidImagePixels.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImagePixelsTest {

  private static final int CAP_SIDE = 4096;

  /** A red pixel, then a blue one. */
  private static byte[] redThenBlue() {
    return new byte[] {(byte) 255, 0, 0, 0, 0, (byte) 255};
  }

  @Test
  @DisplayName("AC-SRCH-53: image pixels hold their width, height and RGB bytes")
  void holds_width_height_and_rgb_bytes() {
    ImagePixels pixels = new ImagePixels(2, 1, redThenBlue());

    assertThat(pixels.width()).isEqualTo(2);
    assertThat(pixels.height()).isEqualTo(1);
    assertThat(pixels.rgb()).containsExactly(redThenBlue());
  }

  @Test
  @DisplayName("AC-SRCH-53: changing the caller's array afterwards does not change the image")
  void copies_the_callers_array() {
    byte[] callers = redThenBlue();
    ImagePixels pixels = new ImagePixels(2, 1, callers);

    callers[0] = 7;

    assertThat(pixels.rgb()).containsExactly(redThenBlue());
  }

  @Test
  @DisplayName("AC-SRCH-53: changing the array rgb() returned does not change the image")
  void returns_a_copy_of_its_bytes() {
    ImagePixels pixels = new ImagePixels(2, 1, redThenBlue());

    pixels.rgb()[0] = 7;

    assertThat(pixels.rgb()).containsExactly(redThenBlue());
  }

  @Test
  @DisplayName("AC-SRCH-53: images with the same dimensions and bytes are equal")
  void equal_when_dimensions_and_bytes_match() {
    ImagePixels one = new ImagePixels(2, 1, redThenBlue());
    ImagePixels other = new ImagePixels(2, 1, redThenBlue());

    assertThat(one).isEqualTo(other);
    assertThat(one.hashCode()).isEqualTo(other.hashCode());
  }

  @Test
  @DisplayName("AC-SRCH-53: images that differ in one byte are not equal")
  void not_equal_when_one_byte_differs() {
    byte[] changed = redThenBlue();
    changed[5] = (byte) 254;

    assertThat(new ImagePixels(2, 1, redThenBlue())).isNotEqualTo(new ImagePixels(2, 1, changed));
  }

  @Test
  @DisplayName("AC-SRCH-53: the same bytes with swapped dimensions are a different image")
  void not_equal_when_dimensions_are_swapped() {
    assertThat(new ImagePixels(2, 1, redThenBlue()))
        .isNotEqualTo(new ImagePixels(1, 2, redThenBlue()));
  }

  @Test
  @DisplayName("search.md 3.6: toString shows the dimensions, never the bytes")
  void describes_itself_by_dimensions_only() {
    String text = new ImagePixels(2, 1, redThenBlue()).toString();

    assertThat(text).contains("2").contains("1").doesNotContain("[B@").doesNotContain("255");
  }

  @Test
  @DisplayName("AC-SRCH-53: a 1 x 1 image is accepted")
  void accepts_a_single_pixel() {
    assertThat(new ImagePixels(1, 1, new byte[3]).width()).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-SRCH-53: a 4,096 x 4,096 image (exactly the pixel cap) is accepted")
  void accepts_exactly_the_pixel_cap() {
    ImagePixels pixels = new ImagePixels(CAP_SIDE, CAP_SIDE, new byte[CAP_SIDE * CAP_SIDE * 3]);

    assertThat(pixels.height()).isEqualTo(CAP_SIDE);
  }

  @ParameterizedTest(name = "{0} x {1}")
  @CsvSource({"0, 1", "1, 0", "0, 0", "-1, 1", "1, -1", "-1, -1"})
  @DisplayName("AC-SRCH-54: a zero or negative side is rejected")
  void rejects_a_non_positive_dimension(int width, int height) {
    // 3 bytes: (-1) x (-1) x 3 would match the data length, so only the dimension rule catches it.
    assertRejected(width, height, new byte[3], Reason.NON_POSITIVE_DIMENSION);
  }

  @ParameterizedTest(name = "{0} x {1}")
  @CsvSource({"4096, 4097", "4097, 4096", "65536, 65536"})
  @DisplayName("AC-SRCH-54: more than 16,777,216 pixels is rejected before the data length")
  void rejects_too_many_pixels(int width, int height) {
    // 65,536 x 65,536 overflows an int product to 0; the cap must still catch it.
    assertRejected(width, height, new byte[3], Reason.TOO_MANY_PIXELS);
  }

  @ParameterizedTest(name = "{0} bytes")
  @ValueSource(ints = {11, 13})
  @DisplayName("AC-SRCH-54: a data length other than width x height x 3 is rejected")
  void rejects_a_data_length_mismatch(int length) {
    assertRejected(2, 2, new byte[length], Reason.DATA_LENGTH_MISMATCH);
  }

  @Test
  @DisplayName("AC-SRCH-54: a null array is rejected")
  void rejects_null_data() {
    assertThatThrownBy(() -> new ImagePixels(2, 2, null)).isInstanceOf(NullPointerException.class);
  }

  private static void assertRejected(int width, int height, byte[] rgb, Reason reason) {
    assertThatThrownBy(() -> new ImagePixels(width, height, rgb))
        .isInstanceOfSatisfying(
            InvalidImagePixels.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }
}
