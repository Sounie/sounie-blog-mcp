package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChunkingPolicyTest {

  private final ChunkingPolicy policy = ChunkingPolicy.standard();

  private List<Chunk> chunk(int words, TokenCounter tokens) {
    return policy.chunk(WordSequence.of(Words.numbered(words)), tokens);
  }

  @ParameterizedTest
  @ValueSource(ints = {120, 300})
  @DisplayName("AC-SRCH-1: a short post is one chunk")
  void short_post_is_one_chunk(int words) {
    List<Chunk> chunks = chunk(words, FakeTokenCounter.perWord(1));

    assertThat(chunks).containsExactly(new Chunk(0, Words.range(1, words)));
  }

  @Test
  @DisplayName("AC-SRCH-2: 700 words give 3 chunks sharing 50 words each")
  void long_post_is_three_overlapping_chunks() {
    List<Chunk> chunks = chunk(700, FakeTokenCounter.perWord(1));

    assertThat(chunks)
        .containsExactly(
            new Chunk(0, Words.range(1, 300)),
            new Chunk(1, Words.range(251, 550)),
            new Chunk(2, Words.range(501, 700)));
  }

  @Test
  @DisplayName("AC-SRCH-2: 301 words give 2 chunks")
  void three_hundred_and_one_words_give_two_chunks() {
    List<Chunk> chunks = chunk(301, FakeTokenCounter.perWord(1));

    assertThat(chunks)
        .containsExactly(new Chunk(0, Words.range(1, 300)), new Chunk(1, Words.range(251, 301)));
  }

  @Test
  @DisplayName("AC-SRCH-3: at 2 tokens per word the token budget closes the first chunk at 200")
  void token_budget_closes_chunk_early() {
    List<Chunk> chunks = chunk(500, FakeTokenCounter.perWord(2));

    assertThat(chunks.get(0).words()).isEqualTo(Words.range(1, 200));
    assertThat(chunks.get(1).words()).startsWith("w151");
  }

  @Test
  @DisplayName("AC-SRCH-3: at 4 tokens per word the overlap is capped at 25 words (100 tokens)")
  void overlap_is_token_capped() {
    List<Chunk> chunks = chunk(500, FakeTokenCounter.perWord(4));

    assertThat(chunks.get(0).words()).isEqualTo(Words.range(1, 100));
    assertThat(chunks.get(1).words()).startsWith("w76");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 7, 64})
  @DisplayName("AC-SRCH-3: with any counter, chunks stay in budget and each adds a new word")
  void every_chunk_is_within_budget_and_adds_a_new_word(int tokensPerWord) {
    assertChunkInvariants(2000, FakeTokenCounter.perWord(tokensPerWord));
  }

  @Test
  @DisplayName(
      "AC-SRCH-3: with an irregular counter, chunks stay in budget and each adds a new word")
  void irregular_costs_keep_the_invariants() {
    assertChunkInvariants(2000, FakeTokenCounter.irregular());
  }

  private void assertChunkInvariants(int wordCount, TokenCounter tokens) {
    List<Chunk> chunks = chunk(wordCount, tokens);

    assertThat(chunks).extracting(Chunk::index).containsExactlyElementsOf(indexes(chunks.size()));
    assertThat(chunks.getFirst().words()).startsWith("w1");
    assertThat(chunks.getLast().words()).endsWith("w" + wordCount);
    chunks.forEach(
        c -> {
          assertThat(c.words()).hasSizeLessThanOrEqualTo(ChunkingPolicy.TARGET_WORDS);
          assertThat(cost(c, tokens)).isLessThanOrEqualTo(ChunkingPolicy.BODY_TOKEN_BUDGET);
        });
    IntStream.range(1, chunks.size())
        .forEach(
            i -> {
              List<String> previous = chunks.get(i - 1).words();
              List<String> current = chunks.get(i).words();
              assertThat(new HashSet<>(previous)).doesNotContain(current.getLast());
              assertThat(previous).contains(current.getFirst());
            });
  }

  private static List<Integer> indexes(int n) {
    return IntStream.range(0, n).boxed().toList();
  }

  private static int cost(Chunk chunk, TokenCounter tokens) {
    return chunk.words().stream().mapToInt(tokens::tokensIn).sum();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  \n\t "})
  @DisplayName("AC-SRCH-4: an empty body is one chunk with empty text")
  void empty_body_is_one_empty_chunk(String body) {
    List<Chunk> chunks = policy.chunk(WordSequence.of(body), FakeTokenCounter.perWord(1));

    assertThat(chunks).containsExactly(new Chunk(0, List.of()));
    assertThat(chunks.getFirst().text()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-5: overlong-word pieces never overflow the token budget")
  void overlong_word_pieces_stay_within_budget() {
    String body = Words.numbered(200) + " " + "QUJD".repeat(250) + " " + Words.numbered("x", 200);
    FakeTokenCounter onePerCharacter = FakeTokenCounter.perCharacter();

    List<Chunk> chunks = policy.chunk(WordSequence.of(body), onePerCharacter);

    assertThat(chunks)
        .allSatisfy(
            c ->
                assertThat(cost(c, onePerCharacter))
                    .isLessThanOrEqualTo(ChunkingPolicy.BODY_TOKEN_BUDGET));
  }

  @Test
  void chunk_text_is_words_joined_by_single_spaces() {
    assertThat(new Chunk(0, List.of("a", "b", "c")).text()).isEqualTo("a b c");
  }
}
