package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx.TestImages.sample;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PilBicubicResizeTest {

  @Test
  @DisplayName("AC-SRCH-58: a single colour stays exactly that colour after downscaling")
  void keeps_a_single_colour() {
    ImagePixels green = TestImages.uniform(1024, 768, 10, 200, 30);

    ResizedImage resized = PilBicubicResize.resize(green, new TargetSize(912, 672));

    assertThat(resized.pixels()).isEqualTo(TestImages.uniform(912, 672, 10, 200, 30));
  }

  @Test
  @DisplayName("AC-SRCH-58: an image already at the target size keeps the same bytes")
  void leaves_an_image_at_the_target_size_unchanged() {
    ImagePixels gradient = TestImages.painted(48, 48, (x, y) -> new int[] {x * 5, y * 5, 255 - x});

    ResizedImage resized = PilBicubicResize.resize(gradient, new TargetSize(48, 48));

    assertThat(resized.pixels()).isEqualTo(gradient);
  }

  @Test
  @DisplayName("AC-SRCH-58: downscaling widens the kernel, so one-pixel stripes average to grey")
  void downscaling_averages_one_pixel_stripes_to_grey() {
    ImagePixels stripes =
        TestImages.painted(
            96, 96, (x, y) -> x % 2 == 0 ? new int[] {0, 0, 0} : new int[] {255, 255, 255});

    ImagePixels resized = PilBicubicResize.resize(stripes, new TargetSize(48, 48)).pixels();

    byte[] rgb = resized.rgb();
    List<Integer> inner = new ArrayList<>();
    for (int y = 0; y < 48; y++) {
      for (int x = 2; x < 46; x++) {
        for (int c = 0; c < 3; c++) {
          inner.add(sample(rgb, 48, x, y, c));
        }
      }
    }
    // Sampling without the widened kernel would give only 0 and 255.
    assertThat(inner).isNotEmpty().allSatisfy(value -> assertThat(value).isBetween(127, 128));
  }

  @Test
  @DisplayName("AC-SRCH-58: the bicubic overshoot at a hard edge is clamped to 0..255")
  void clamps_the_overshoot_at_a_hard_edge() {
    ImagePixels edge =
        TestImages.painted(4, 1, (x, y) -> x < 2 ? new int[] {0, 0, 0} : new int[] {255, 255, 255});

    ImagePixels resized = PilBicubicResize.resize(edge, new TargetSize(8, 1)).pixels();

    byte[] rgb = resized.rgb();
    List<Integer> samples = IntStream.range(0, 8).mapToObj(x -> sample(rgb, 8, x, 0, 0)).toList();
    assertThat(samples).allSatisfy(value -> assertThat(value).isBetween(0, 255));
    assertThat(samples).contains(0, 255);
    // An unclamped overshoot would wrap around as a byte (-3 -> 253), breaking the dark-to-light
    // order; PIL gives [0, 0, 0, 52, 203, 255, 255, 255].
    assertThat(samples).isSorted();
  }

  @Test
  @DisplayName("AC-SRCH-58: the result is a resized image of exactly the target size")
  void result_has_the_target_size() {
    TargetSize target = new TargetSize(672, 912);

    ResizedImage resized = PilBicubicResize.resize(TestImages.uniform(768, 1024, 1, 2, 3), target);

    assertThat(resized.size()).isEqualTo(target);
    assertThat(resized.pixels().width()).isEqualTo(672);
    assertThat(resized.pixels().height()).isEqualTo(912);
  }
}
