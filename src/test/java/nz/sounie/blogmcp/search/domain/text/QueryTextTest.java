package nz.sounie.blogmcp.search.domain.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryTextTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\n\t"})
  @DisplayName("AC-SRCH-19: a blank query is rejected as BLANK")
  void rejects_blank_text(String text) {
    assertThatThrownBy(() -> new QueryText(text))
        .isInstanceOfSatisfying(
            InvalidSearchQuery.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidSearchQuery.Reason.BLANK));
  }

  @Test
  @DisplayName("AC-SRCH-19: 1,001 characters after trimming is TOO_LONG")
  void rejects_overlong_text() {
    assertThatThrownBy(() -> new QueryText("q".repeat(1001)))
        .isInstanceOfSatisfying(
            InvalidSearchQuery.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidSearchQuery.Reason.TOO_LONG));
  }

  @Test
  @DisplayName("AC-SRCH-19: exactly 1,000 characters is accepted")
  void accepts_1000_characters() {
    assertThat(new QueryText("q".repeat(1000)).value()).hasSize(1000);
  }

  @Test
  @DisplayName("AC-SRCH-19: surrounding whitespace is trimmed before the length check")
  void trims_before_checking_length() {
    assertThat(new QueryText("  " + "q".repeat(1000) + "\n").value()).hasSize(1000);
  }

  @Test
  @DisplayName("AC-SRCH-19: surrounding whitespace is trimmed")
  void trims_surrounding_whitespace() {
    assertThat(new QueryText("  records in java \n").value()).isEqualTo("records in java");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\u00A0\u00A0", "\u2003\u3000", "\u202F", "\u001F\u001C", " \u00A0\t"})
  @DisplayName("S5: a query of only Unicode white space (as WordSequence defines it) is BLANK")
  void rejects_unicode_white_space_as_blank(String text) {
    assertThatThrownBy(() -> new QueryText(text))
        .isInstanceOfSatisfying(
            InvalidSearchQuery.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidSearchQuery.Reason.BLANK));
  }

  @Test
  @DisplayName("S5: no-break spaces around the query are trimmed")
  void trims_no_break_spaces() {
    assertThat(new QueryText("\u00A0records in java\u2003").value()).isEqualTo("records in java");
  }
}
