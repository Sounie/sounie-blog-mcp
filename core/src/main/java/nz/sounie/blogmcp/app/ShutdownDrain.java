package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.time.Duration;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;

/**
 * The JVM shutdown hook that tells the provider the process is exiting and waits at most {@link
 * #MAX_WAIT} for its running model call (app.md 3.4, AC-APP-48). Writes one stderr line only if the
 * wait timed out.
 */
public final class ShutdownDrain implements Runnable {

  /** The longest the exit waits for a running model call. */
  public static final Duration MAX_WAIT = Duration.ofSeconds(5);

  public ShutdownDrain(EmbeddingProvider provider, PrintStream errors) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void run() {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
