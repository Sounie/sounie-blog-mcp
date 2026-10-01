package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResultLimitTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"-5, 1", "0, 1", "1, 1", "7, 7", "20, 20", "21, 20"})
  @DisplayName("AC-SRCH-20: the limit is clamped to 1..20")
  void clamps_to_range(int requested, int expected) {
    assertThat(ResultLimit.of(requested).value()).isEqualTo(expected);
  }

  @Test
  @DisplayName("AC-SRCH-20: the default limit is 10")
  void default_is_ten() {
    assertThat(ResultLimit.defaultLimit().value()).isEqualTo(10);
  }
}
