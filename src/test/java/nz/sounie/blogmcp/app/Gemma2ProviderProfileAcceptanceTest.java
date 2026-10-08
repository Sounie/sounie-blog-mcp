package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("jar")
class Gemma2ProviderProfileAcceptanceTest {

  private static final String REVISION = "914f7f89142e33e77833254d9c9b90c3cef7303b";

  @Test
  @DisplayName("AC-SRCH-46, 47, 49: the Gemma2 artifact uses its pinned profile and real tokenizer")
  void uses_the_pinned_gemma2_profile_for_input_preparation_and_budget_checks() {
    Assumptions.assumeTrue("gemma2".equals(System.getProperty("blogmcp.embeddingProvider")));
    EmbeddingProvider provider = selectedProvider();
    EmbeddingProviderProfile profile = provider.profile();

    assertThat(provider.modelId()).containsIgnoringCase("embeddinggemma");
    assertThat(profile.modelRevision()).isEqualTo(REVISION);
    assertThat(profile.tokenizerRevision()).isEqualTo(REVISION);
    assertThat(profile.dimension()).isEqualTo(768);
    assertThat(profile.contextTokens()).isEqualTo(8192);
    assertThat(profile.indexProfileKey()).isEqualTo("gemma2-914f7f89-v1");
    assertThat(profile.tokenizer()).isNotNull();
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

  private static EmbeddingProvider selectedProvider() {
    return EmbeddingProviderDiscovery.discover(Thread.currentThread().getContextClassLoader());
  }
}
