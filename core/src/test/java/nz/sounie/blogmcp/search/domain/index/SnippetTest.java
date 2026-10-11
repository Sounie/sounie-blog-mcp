package nz.sounie.blogmcp.search.domain.index;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.domain.text.Words;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SnippetTest {

  @Test
  @DisplayName("AC-SRCH-39: a chunk longer than 60 words is cut to 60 words and marked")
  void cuts_a_long_chunk_to_sixty_words_and_marks_it() {
    Snippet snippet = Snippet.of(Words.numbered(300));

    assertThat(snippet.text()).isEqualTo(Words.numbered(60) + " …");
    assertThat(snippet.truncated()).isTrue();
  }

  @Test
  @DisplayName("AC-SRCH-39: one word over the limit is enough to cut")
  void cuts_a_chunk_of_sixty_one_words() {
    Snippet snippet = Snippet.of(Words.numbered(61));

    assertThat(snippet.text()).isEqualTo(Words.numbered(60) + " …");
    assertThat(snippet.truncated()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {60, 40, 1})
  @DisplayName("AC-SRCH-39: a chunk of at most 60 words is the snippet unchanged")
  void keeps_a_short_chunk_unchanged(int words) {
    String chunk = Words.numbered(words);

    Snippet snippet = Snippet.of(chunk);

    assertThat(snippet.text()).isEqualTo(chunk);
    assertThat(snippet.truncated()).isFalse();
  }

  @Test
  @DisplayName("AC-SRCH-39: an empty chunk (empty-body post) gives an empty snippet")
  void an_empty_chunk_gives_an_empty_snippet() {
    Snippet snippet = Snippet.of("");

    assertThat(snippet.text()).isEmpty();
    assertThat(snippet.truncated()).isFalse();
  }
}
