package nz.sounie.blogmcp.search.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VectorCodecTest {

  @Test
  @DisplayName("AC-APP-25: values are little-endian float32, in standard base64")
  void encodes_little_endian_float32_as_base64() {
    // 1.0f is 0x3F800000; little-endian bytes 00 00 80 3F.
    assertThat(VectorCodec.encode(new float[] {1.0f})).isEqualTo("AACAPw==");
  }

  @Test
  @DisplayName("AC-APP-25: edge values round-trip bit for bit")
  void edge_values_round_trip_bit_exactly() {
    float[] values = {
      -0.0f,
      0.0f,
      Float.MIN_VALUE,
      1.0e-40f,
      -1.0e-40f,
      Float.MIN_NORMAL,
      Float.MAX_VALUE,
      -Float.MAX_VALUE,
      0.1f,
      -1.0f,
      Math.nextUp(1.0f)
    };

    float[] decoded = VectorCodec.decode(VectorCodec.encode(values));

    assertThat(rawBits(decoded)).containsExactly(rawBits(values));
  }

  @Test
  @DisplayName("AC-APP-25: an embedding of 384 values is 1,536 bytes and round-trips exactly")
  void a_full_embedding_round_trips() {
    float[] values = TestEmbeddings.irregular(7).values();

    String encoded = VectorCodec.encode(values);

    assertThat(encoded).hasSize(2_048); // base64 of 1,536 bytes
    assertThat(rawBits(VectorCodec.decode(encoded))).containsExactly(rawBits(values));
    assertThat(VectorCodec.decode(encoded)).hasSize(384);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not*base64!", "AACAPw=", "AACA Pw=="})
  @DisplayName("AC-APP-28: text that is not base64 is rejected")
  void rejects_text_that_is_not_base64(String text) {
    assertThatThrownBy(() -> VectorCodec.decode(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("AC-APP-28: bytes that are not a whole number of float32 values are rejected")
  void rejects_a_partial_float() {
    String fiveBytes = "AAAAAAA=";

    assertThatThrownBy(() -> VectorCodec.decode(fiveBytes))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static int[] rawBits(float[] values) {
    return IntStream.range(0, values.length).map(i -> Float.floatToRawIntBits(values[i])).toArray();
  }

  @Test
  void decodes_nothing_to_no_values() {
    assertThat(VectorCodec.decode("")).isEmpty();
  }
}
