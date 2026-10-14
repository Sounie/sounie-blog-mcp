package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbedderShutdown;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-APP-48: the shutdown hook drains the provider for at most 5 s, logging only a timeout. */
class ShutdownDrainTest {

  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(stderr, true, StandardCharsets.UTF_8);

  /** A fake of our own provider SPI that records the wait it was given. */
  private static final class ShuttingDownProvider
      extends EmbeddingProviderDiscoveryTest.TestProvider {
    private final EmbedderShutdown outcome;
    private final List<Duration> waits = new CopyOnWriteArrayList<>();

    ShuttingDownProvider(EmbedderShutdown outcome) {
      this.outcome = outcome;
    }

    @Override
    public String modelId() {
      return "slow-model@rev";
    }

    @Override
    public EmbedderShutdown shutDown(Duration maxWait) {
      waits.add(maxWait);
      return outcome;
    }
  }

  private String stderr() {
    return stderr.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("AC-APP-48: the drain asks the provider to shut down with a wait of 5 s, once")
  void shuts_the_provider_down_with_the_maximum_wait() {
    ShuttingDownProvider provider = new ShuttingDownProvider(EmbedderShutdown.DRAINED);

    new ShutdownDrain(provider, errors).run();

    assertThat(ShutdownDrain.MAX_WAIT).isEqualTo(Duration.ofSeconds(5));
    assertThat(provider.waits).containsExactly(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("AC-APP-48: a drained provider writes nothing to stderr")
  void writes_nothing_when_drained() {
    new ShutdownDrain(new ShuttingDownProvider(EmbedderShutdown.DRAINED), errors).run();

    assertThat(stderr()).isEmpty();
  }

  @Test
  @DisplayName("AC-APP-48: a timed-out drain writes one stderr line naming the model")
  void writes_one_line_naming_the_model_when_timed_out() {
    new ShutdownDrain(new ShuttingDownProvider(EmbedderShutdown.TIMED_OUT), errors).run();

    assertThat(stderr().lines().toList())
        .singleElement(InstanceOfAssertFactories.STRING)
        .contains("slow-model@rev");
  }
}
