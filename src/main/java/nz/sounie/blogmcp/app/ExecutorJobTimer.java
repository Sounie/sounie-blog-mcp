package nz.sounie.blogmcp.app;

import java.time.Duration;

/** The production {@link JobTimer}: one daemon thread, fixed delay. */
public final class ExecutorJobTimer implements JobTimer, AutoCloseable {

  @Override
  public void runNowThenEvery(Duration delay, Runnable job) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** Stops scheduling further runs. */
  @Override
  public void close() {
    // red: nothing to stop yet
  }
}
