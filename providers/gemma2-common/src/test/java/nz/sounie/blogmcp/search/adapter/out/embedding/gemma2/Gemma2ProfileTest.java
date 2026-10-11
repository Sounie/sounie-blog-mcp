package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Gemma2ProfileTest {

  private final Gemma2TokenCounter tokens = new Gemma2TokenCounter();

  @Test
  @DisplayName("AC-SRCH-47, 51: both Gemma2 providers share the pinned tokenizer and budget")
  void builds_the_shared_profile_for_a_model_revision_and_key() {
    EmbeddingProviderProfile profile = Gemma2Profile.of("model-revision", "profile-key", tokens);

    assertThat(profile.modelRevision()).isEqualTo("model-revision");
    assertThat(profile.tokenizerRevision()).isEqualTo("914f7f89142e33e77833254d9c9b90c3cef7303b");
    assertThat(profile.indexProfileKey()).isEqualTo("profile-key");
    assertThat(profile.dimension()).isEqualTo(768);
    assertThat(profile.contextTokens()).isEqualTo(8192);
    assertThat(profile.passageTokenBudget()).isEqualTo(Gemma2Profile.BUDGET);
    assertThat(profile.tokenizer()).isSameAs(tokens);
  }

  @Test
  void prepares_a_passage_within_the_budget() {
    assertThat(Gemma2Profile.preparePassage("Title", new Passage("body"), tokens))
        .isEqualTo("title: Title | text: body");
  }

  @Test
  void rejects_a_prepared_passage_over_the_budget() {
    Passage unbounded = new Passage("unbounded passage ".repeat(4096));

    assertThatThrownBy(() -> Gemma2Profile.preparePassage("", unbounded, tokens))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeding the 4096-token budget");
  }
}
