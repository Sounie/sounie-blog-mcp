package nz.sounie.blogmcp.search.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import nz.sounie.blogmcp.search.domain.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.Passage;
import nz.sounie.blogmcp.search.domain.PassageComposition;
import nz.sounie.blogmcp.search.domain.WordSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The real tokenizer (bge-small-en-v1.5-q-tokenizer.json through DJL); no test doubles. */
@Tag("model")
class BgeTokenCounterTest {

  private final BgeTokenCounter counter = RealModel.tokenCounter();

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"hello, 1", "java, 1", "build.gradle.kts, 8"})
  void counts_word_piece_tokens_without_special_tokens(String word, int tokens) {
    assertThat(counter.tokensIn(word)).isEqualTo(tokens);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Records in Java 25 are transparent carriers for immutable data.",
        "list.stream().map(String::trim).toList(); // see https://openjdk.org/jeps/395",
        "AbstractSingletonProxyFactoryBean!!!???... {{{}}} QUJDREVGR0g="
      })
  @DisplayName("Word costs add up to the cost of the whole text (chunking relies on this)")
  void word_costs_are_additive(String text) {
    int sumOfWords = WordSequence.of(text).words().stream().mapToInt(counter::tokensIn).sum();

    assertThat(sumOfWords).isEqualTo(RealModel.contentTokens(text));
  }

  @Test
  void a_64_character_piece_costs_at_most_64_tokens() {
    assertThat(counter.tokensIn("!@#$%^&*()".repeat(7).substring(0, 64))).isBetween(1, 64);
  }

  @Test
  @DisplayName("AC-SRCH-8: every passage of a pathological post fits in one model partition")
  void every_passage_fits_in_one_partition() {
    String title = pathologicalWords(120, new Random(7));
    String body = pathologicalWords(3000, new Random(11)) + " " + noWhitespace(2000);

    var words = WordSequence.of(body);
    var chunks = ChunkingPolicy.standard().chunk(words, counter);
    List<Passage> passages = PassageComposition.standard().compose(title, chunks, counter);

    assertThat(passages).hasSizeGreaterThan(5);
    assertThat(passages)
        .allSatisfy(p -> assertThat(RealModel.contentTokens(p.text())).isLessThanOrEqualTo(465));
  }

  private static final List<String> FRAGMENTS =
      Arrays.asList(
          "public",
          "static",
          "<T",
          "extends",
          "Comparable<?",
          "super",
          "T>>",
          "void",
          "sort(List<T>",
          "list)",
          "{",
          "list.sort(null);",
          "}",
          "AbstractSingletonProxyFactoryBeanConfigurationPropertiesBindingPostProcessor",
          "!!!???...;;;:::{{{}}}",
          "->",
          "::",
          "==",
          "!=",
          "&&",
          "||",
          "https://example.com/path/to/resource?query=value&other=1#fragment",
          "the",
          "records",
          "are",
          "immutable",
          "carriers",
          "of",
          "data.",
          "0x7fffffff",
          "e^(iπ)+1=0",
          "naïve",
          "café",
          "日本語のテキスト",
          "대한민국의프로그래머들이자바레코드와패턴매칭을사용하여불변데이터를표현하는방법에대한긴설명");

  private static String pathologicalWords(int count, Random random) {
    List<String> words = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      words.add(FRAGMENTS.get(random.nextInt(FRAGMENTS.size())));
    }
    return String.join(" ", words);
  }

  private static String noWhitespace(int length) {
    String alphabet = "aZ9+/=!?.,;:'\"()[]{}<>@#$%^&*_-~`|\\";
    Random random = new Random(3);
    StringBuilder text = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      text.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return text.toString();
  }

  @Test
  @DisplayName("S11: the AC-SRCH-8 oracle does not truncate at 512 tokens")
  void oracle_does_not_truncate() {
    String text = "word ".repeat(700);

    assertThat(RealModel.contentTokens(text)).isGreaterThan(512).isEqualTo(700);
  }
}
