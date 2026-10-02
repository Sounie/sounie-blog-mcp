package nz.sounie.blogmcp.search.domain.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PassageCompositionTest {

  private final PassageComposition composition = PassageComposition.standard();
  private final TokenCounter onePerWord = FakeTokenCounter.perWord(1);

  private static final List<Chunk> THREE_CHUNKS =
      List.of(
          new Chunk(0, Words.range(1, 300)),
          new Chunk(1, Words.range(251, 550)),
          new Chunk(2, Words.range(501, 700)));

  @Test
  @DisplayName("AC-SRCH-7: each passage is the title, a newline, then the chunk text")
  void each_passage_is_title_line_then_chunk_text() {
    List<Passage> passages = composition.compose("Records in Java 25", THREE_CHUNKS, onePerWord);

    assertThat(passages)
        .containsExactly(
            new Passage("Records in Java 25\n" + String.join(" ", Words.range(1, 300))),
            new Passage("Records in Java 25\n" + String.join(" ", Words.range(251, 550))),
            new Passage("Records in Java 25\n" + String.join(" ", Words.range(501, 700))));
  }

  @Test
  @DisplayName("AC-SRCH-7: the title line is the normalised title")
  void title_is_normalised() {
    List<Passage> passages =
        composition.compose(
            " Records\tin  Java\n25 ", List.of(new Chunk(0, List.of("a"))), onePerWord);

    assertThat(passages).containsExactly(new Passage("Records in Java 25\na"));
  }

  @Test
  @DisplayName("AC-SRCH-7: a title over 64 tokens is cut to its longest whole-word prefix")
  void long_title_is_cut_to_64_tokens() {
    List<Passage> passages =
        composition.compose(
            Words.numbered("t", 100), List.of(new Chunk(0, List.of("body"))), onePerWord);

    assertThat(passages).containsExactly(new Passage(Words.numbered("t", 64) + "\nbody"));
  }

  @Test
  @DisplayName("AC-SRCH-7: the title cut counts tokens, not words")
  void title_cut_counts_tokens() {
    List<Passage> passages =
        composition.compose(
            Words.numbered("t", 100),
            List.of(new Chunk(0, List.of("body"))),
            FakeTokenCounter.perWord(3));

    assertThat(passages).containsExactly(new Passage(Words.numbered("t", 21) + "\nbody"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  \t"})
  @DisplayName("AC-SRCH-7: with a blank title the passage is the chunk text alone")
  void blank_title_is_omitted(String title) {
    List<Passage> passages =
        composition.compose(title, List.of(new Chunk(0, List.of("a", "b"))), onePerWord);

    assertThat(passages).containsExactly(new Passage("a b"));
  }

  @Test
  @DisplayName("AC-SRCH-4: an empty chunk's passage is exactly the title")
  void empty_chunk_passage_is_the_title() {
    List<Passage> passages =
        composition.compose("Hello", List.of(new Chunk(0, List.of())), onePerWord);

    assertThat(passages).containsExactly(new Passage("Hello"));
  }

  @Test
  void no_chunks_give_no_passages() {
    assertThat(composition.compose("Hello", List.of(), onePerWord)).isEmpty();
  }
}
