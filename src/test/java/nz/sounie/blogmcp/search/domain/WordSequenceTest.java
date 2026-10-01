package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WordSequenceTest {

  @Test
  @DisplayName("AC-SRCH-6: tabs, newlines and runs of spaces separate words")
  void whitespace_runs_separate_words() {
    assertThat(WordSequence.of("a\tb\n\nc d   e").words()).containsExactly("a", "b", "c", "d", "e");
  }

  @Test
  @DisplayName("AC-SRCH-6: a no-break space separates words")
  void no_break_space_separates_words() {
    assertThat(WordSequence.of("a b").words()).containsExactly("a", "b");
  }

  @Test
  @DisplayName("AC-SRCH-6: leading and trailing whitespace is dropped")
  void leading_and_trailing_whitespace_is_dropped() {
    assertThat(WordSequence.of("  \n a b \t").words()).containsExactly("a", "b");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\n\t", "   "})
  @DisplayName("AC-SRCH-4: blank text is an empty word sequence")
  void blank_text_is_empty(String text) {
    WordSequence words = WordSequence.of(text);

    assertThat(words.words()).isEmpty();
    assertThat(words.isEmpty()).isTrue();
  }

  @Test
  void non_blank_text_is_not_empty() {
    assertThat(WordSequence.of("a").isEmpty()).isFalse();
  }

  @Test
  @DisplayName("AC-SRCH-6: the normalised text joins words with single spaces")
  void text_joins_words_with_single_spaces() {
    assertThat(WordSequence.of("a\tb\n\nc d   e").text()).isEqualTo("a b c d e");
  }

  @Test
  @DisplayName("AC-SRCH-5: a 1,000-character word becomes 15 pieces of 64 and 1 of 40")
  void splits_overlong_word_into_pieces() {
    String base64 = "QUJD".repeat(250);

    List<String> words = WordSequence.of("before " + base64 + " after").words();

    List<String> pieces = words.subList(1, words.size() - 1);
    assertThat(words).hasSize(18).startsWith("before").endsWith("after");
    assertThat(pieces).hasSize(16);
    assertThat(pieces.subList(0, 15)).allSatisfy(piece -> assertThat(piece).hasSize(64));
    assertThat(pieces.get(15)).hasSize(40);
    assertThat(String.join("", pieces)).isEqualTo(base64);
  }

  @Test
  void a_word_of_exactly_64_characters_is_not_split() {
    assertThat(WordSequence.of("x".repeat(WordSequence.MAX_WORD_CHARS)).words())
        .containsExactly("x".repeat(64));
  }

  @Test
  void a_word_of_65_characters_becomes_two_pieces() {
    assertThat(WordSequence.of("y".repeat(65)).words()).containsExactly("y".repeat(64), "y");
  }
}
