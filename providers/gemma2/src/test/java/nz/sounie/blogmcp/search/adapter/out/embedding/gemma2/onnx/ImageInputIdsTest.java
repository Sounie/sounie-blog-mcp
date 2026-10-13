package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageInputIdsTest {

  private static final long IMAGE_SOFT_TOKEN = 258880;

  @Test
  @DisplayName("AC-SRCH-60: one image token is wrapped in begin, image markers and end")
  void wraps_a_single_image_token() {
    assertThat(ImageInputIds.of(new PatchGrid(3, 3))).containsExactly(2, 255999, 258880, 258882, 1);
  }

  @Test
  @DisplayName("AC-SRCH-60: 266 image tokens give 270 ids with no task prefix")
  void wraps_266_image_tokens() {
    long[] ids = ImageInputIds.of(new PatchGrid(57, 42));

    assertThat(ids).hasSize(270);
    assertThat(Arrays.copyOfRange(ids, 0, 2)).containsExactly(2, 255999);
    assertThat(Arrays.stream(ids, 2, 268).allMatch(id -> id == IMAGE_SOFT_TOKEN)).isTrue();
    assertThat(Arrays.copyOfRange(ids, 268, 270)).containsExactly(258882, 1);
  }
}
