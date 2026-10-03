package nz.sounie.blogmcp.app;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Notices stdin reaching end of file (Claude has gone away), so {@code Main} can exit. */
public final class EndOfInputWatch {

  private final CountDownLatch ended = new CountDownLatch(1);
  private final InputStream stream;

  private EndOfInputWatch(InputStream in) {
    this.stream = new Watched(in);
  }

  /** Watches the stream; the transport reads it through {@link #stream()}. */
  public static EndOfInputWatch wrap(InputStream in) {
    return new EndOfInputWatch(in);
  }

  /** The watched stream, passing every byte through unchanged. */
  public InputStream stream() {
    return stream;
  }

  /** Waits up to the timeout for end of input; whether it was reached. */
  public boolean awaitEnd(Duration timeout) throws InterruptedException {
    return ended.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  /** Waits for end of input, however long it takes. */
  public void awaitEnd() throws InterruptedException {
    ended.await();
  }

  /** Passes reads through, and notes the end of file. */
  private final class Watched extends FilterInputStream {

    Watched(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      return noting(super.read());
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      return noting(super.read(buffer, offset, length));
    }

    private int noting(int result) {
      if (result < 0) {
        ended.countDown();
      }
      return result;
    }
  }
}
