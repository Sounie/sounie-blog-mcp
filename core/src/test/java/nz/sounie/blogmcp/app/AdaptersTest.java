package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptersTest {

  @Test
  @DisplayName(
      "Review #7: the index recipe uses the passage-preparation version the provider declares")
  void takes_the_passage_preparation_version_from_the_provider() {
    Adapters adapters =
        Adapters.withProvider(
            Map.of(), new EmbeddingProviderDiscoveryTest.TestProvider(), Clock.systemUTC());

    assertThat(adapters.passagePreparationVersion()).isEqualTo("test-preparation-v1");
  }
}
