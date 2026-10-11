package nz.sounie.blogmcp.app;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** The production {@link JobTimer}: one daemon thread, fixed delay. */
public final class ExecutorJobTimer implements JobTimer, AutoCloseable {

  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("sync-and-reconcile").factory());

  @Override
  public void runNowThenEvery(Duration delay, Runnable job) {
    executor.scheduleWithFixedDelay(job, 0, delay.toNanos(), TimeUnit.NANOSECONDS);
  }

  /** Stops scheduling further runs. */
  @Override
  public void close() {
    executor.shutdownNow();
  }
}
