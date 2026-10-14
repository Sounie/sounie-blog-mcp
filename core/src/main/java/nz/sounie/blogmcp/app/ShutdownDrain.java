package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.time.Duration;
import java.util.Objects;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbedderShutdown;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;

/**
 * The JVM shutdown hook that tells the provider the process is exiting and waits at most {@link
 * #MAX_WAIT} for its running model call (app.md 3.4, AC-APP-48). Writes one stderr line only if the
 * wait timed out.
 */
public final class ShutdownDrain implements Runnable {

  /** The longest the exit waits for a running model call. */
  public static final Duration MAX_WAIT = Duration.ofSeconds(5);

  private final EmbeddingProvider provider;
  private final PrintStream errors;

  public ShutdownDrain(EmbeddingProvider provider, PrintStream errors) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.errors = Objects.requireNonNull(errors, "errors");
  }

  @Override
  public void run() {
    if (provider.shutDown(MAX_WAIT) == EmbedderShutdown.TIMED_OUT) {
      errors.println(
          "blog-mcp: exiting while the embedding model "
              + provider.modelId()
              + " is still running after "
              + MAX_WAIT.toSeconds()
              + " s");
    }
  }
}
