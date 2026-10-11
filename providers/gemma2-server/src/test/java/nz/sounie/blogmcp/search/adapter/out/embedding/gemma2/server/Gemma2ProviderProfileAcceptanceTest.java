package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.search.domain.text.WordSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pinned profile and real tokenizer; no model server is called. */
class Gemma2ProviderProfileAcceptanceTest {

  private static final String REVISION = "914f7f89142e33e77833254d9c9b90c3cef7303b";
  private final EmbeddingProvider provider = new Gemma2Embedder();

  @Test
  @DisplayName("AC-SRCH-46, 47, 49: the Gemma2 artifact uses its pinned profile and real tokenizer")
  void uses_the_pinned_gemma2_profile_for_input_preparation_and_budget_checks() {
    EmbeddingProviderProfile profile = provider.profile();

    assertThat(provider.modelId()).containsIgnoringCase("embeddinggemma");
    assertThat(profile.modelRevision()).isEqualTo(REVISION);
    assertThat(profile.tokenizerRevision()).isEqualTo(REVISION);
    assertThat(profile.dimension()).isEqualTo(768);
    assertThat(profile.contextTokens()).isEqualTo(8192);
    assertThat(profile.indexProfileKey()).isEqualTo("gemma2-914f7f89-v1");
    assertThat(profile.tokenizer()).isNotNull();
    assertThat(profile.passageTokenBudget().bodyTokens()).isEqualTo(3000);
    assertThat(profile.passageTokenBudget().overlapTokens()).isEqualTo(256);
    assertThat(profile.passageTokenBudget().titleTokens()).isEqualTo(128);
    assertThat(profile.passageTokenBudget().preparedPassageTokens()).isEqualTo(4096);
    assertThat(profile.passageTokenBudget().overlapTokens())
        .isBetween(0, profile.passageTokenBudget().bodyTokens() - 1);
    assertThat(profile.passageTokenBudget().bodyTokens())
        .isLessThanOrEqualTo(profile.passageTokenBudget().preparedPassageTokens());
    assertThat(profile.passageTokenBudget().titleTokens())
        .isLessThanOrEqualTo(profile.passageTokenBudget().preparedPassageTokens());
    assertThat(profile.passageTokenBudget().preparedPassageTokens())
        .isLessThanOrEqualTo(profile.contextTokens());

    assertThat(provider.prepareQuery(new QueryText("records in java")))
        .isEqualTo("task: search result | query: records in java");
    assertThat(provider.preparePassage("", new Passage("canonical passage")))
        .isEqualTo("title: none | text: canonical passage");
    int measured = profile.tokenizer().countTokens("title: none | text: canonical passage");
    assertThat(measured).isBetween(0, profile.contextTokens());
  }

  @Test
  @DisplayName("AC-SRCH-47: chunking and the prepared-passage limit use the pinned tokenizer")
  void validates_gemma2_passage_budgets_with_the_pinned_tokenizer() {
    EmbeddingProviderProfile profile = provider.profile();
    var budget = profile.passageTokenBudget();
    String title = "Māori 日本語 🌏 — ".repeat(120);
    String body = "supercalifragilisticexpialidocious!? Māori 日本語 🌏 — ".repeat(2000);
    var chunks =
        new ChunkingPolicy(
                ChunkingPolicy.TARGET_WORDS,
                budget.bodyTokens(),
                ChunkingPolicy.OVERLAP_WORDS,
                budget.overlapTokens())
            .chunk(WordSequence.of(body), provider.tokenCounter());
    var passages =
        new PassageComposition(budget.titleTokens(), PassageComposition.VERSION)
            .compose(
                title,
                chunks,
                provider.tokenCounter(),
                (preparedTitle, chunk) ->
                    new Passage(provider.preparePassage(preparedTitle, new Passage(chunk))));

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(passages)
        .allSatisfy(
            passage ->
                assertThat(profile.tokenizer().countTokens(passage.text()))
                    .isLessThanOrEqualTo(budget.preparedPassageTokens()));
    assertThatThrownBy(
            () ->
                provider.preparePassage(
                    "", new Passage("unbounded passage ".repeat(budget.preparedPassageTokens()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeding the 4096-token budget");
  }
}
