package nz.sounie.blogmcp.app;

import java.io.InputStream;
import java.time.Duration;

/** Notices stdin reaching end of file (Claude has gone away), so {@code Main} can exit. */
public final class EndOfInputWatch {

  private EndOfInputWatch() {}

  /** Watches the stream; the transport reads it through {@link #stream()}. */
  public static EndOfInputWatch wrap(InputStream in) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** The watched stream, passing every byte through unchanged. */
  public InputStream stream() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** Waits up to the timeout for end of input; whether it was reached. */
  public boolean awaitEnd(Duration timeout) throws InterruptedException {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }
}
