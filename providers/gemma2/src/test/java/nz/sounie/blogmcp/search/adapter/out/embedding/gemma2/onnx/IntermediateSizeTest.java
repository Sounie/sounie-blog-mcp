package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntermediateSizeTest {

  /** 48 × 349,525 × 3 = 50,331,600 samples: the tallest 48-wide intermediate within 48 MiB. */
  private static final int TALLEST_WITHIN_BOUND = 349_525;

  @Test
  @DisplayName("AC-SRCH-65: an intermediate image at the 48 MiB bound is accepted")
  void accepts_an_intermediate_image_within_the_bound() {
    assertThat(new IntermediateSize(48, TALLEST_WITHIN_BOUND).samples())
        .isEqualTo(48 * TALLEST_WITHIN_BOUND * 3);
  }

  @Test
  @DisplayName("AC-SRCH-65: an intermediate image over the 48 MiB bound is refused")
  void refuses_an_intermediate_image_over_the_bound() {
    assertThatThrownBy(() -> new IntermediateSize(48, TALLEST_WITHIN_BOUND + 1))
        .isInstanceOf(ImageTooLargeToResize.class);
  }

  @Test
  @DisplayName("AC-SRCH-65: a size whose sample count overflows an int is refused, not wrapped")
  void refuses_a_size_whose_sample_count_overflows_an_int() {
    assertThatThrownBy(() -> new IntermediateSize(48, (int) ImagePixels.MAX_PIXELS))
        .isInstanceOf(ImageTooLargeToResize.class)
        .hasMessageContaining("48 x 16777216");
  }
}
