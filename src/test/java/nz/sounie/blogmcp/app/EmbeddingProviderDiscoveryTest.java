package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import nz.sounie.blogmcp.search.application.QueryEmbedder;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.embedding.PassageEmbedder;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingProviderDiscoveryTest {

  private static final EmbeddingProvider ONLY_PROVIDER = new TestProvider();

  @Test
  @DisplayName("AC-SRCH-42: startup rejects a runtime classpath with no provider")
  void rejects_zero_discovered_providers() {
    assertThatThrownBy(() -> EmbeddingProviderDiscovery.requireExactlyOne(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  @DisplayName("AC-SRCH-42: startup rejects a runtime classpath with multiple providers")
  void rejects_multiple_discovered_providers() {
    assertThatThrownBy(
            () ->
                EmbeddingProviderDiscovery.requireExactlyOne(
                    List.of(ONLY_PROVIDER, new TestProvider())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  @DisplayName(
      "AC-SRCH-42, 44: one discovered provider object implements both consumer-owned ports")
  void returns_the_single_provider_instance_for_both_ports() {
    EmbeddingProvider provider =
        EmbeddingProviderDiscovery.requireExactlyOne(List.of(ONLY_PROVIDER));

    assertThat(provider).isSameAs(ONLY_PROVIDER);
    assertThat(provider).isInstanceOf(PassageEmbedder.class);
    assertThat(provider).isInstanceOf(QueryEmbedder.class);
  }

  public static class TestProvider implements EmbeddingProvider {

    @Override
    public String modelId() {
      return "test-model";
    }

    @Override
    public java.util.List<nz.sounie.blogmcp.search.domain.embedding.Embedding> embedPassages(
        java.util.List<nz.sounie.blogmcp.search.domain.text.Passage> passages) {
      throw new UnsupportedOperationException();
    }

    @Override
    public nz.sounie.blogmcp.search.domain.embedding.Embedding embedQuery(
        nz.sounie.blogmcp.search.domain.query.QueryPassage query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public EmbeddingProviderProfile profile() {
      return new EmbeddingProviderProfile(
          "test-revision",
          "test-revision",
          "test-profile-v1",
          3,
          8,
          new PassageTokenBudget(3, 1, 1, 4, 8),
          ignored -> 1);
    }

    @Override
    public TokenCounter tokenCounter() {
      return ignored -> 1;
    }

    @Override
    public String prepareQuery(nz.sounie.blogmcp.search.domain.query.QueryText query) {
      return query.value();
    }

    @Override
    public String preparePassage(
        String title, nz.sounie.blogmcp.search.domain.text.Passage passage) {
      return passage.text();
    }
  }
}
