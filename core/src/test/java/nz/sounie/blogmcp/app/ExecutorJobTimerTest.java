package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AC-APP-10: the production timer runs at once, then with a fixed delay, never overlapping. */
class ExecutorJobTimerTest {

  private record Run(long startNanos, long endNanos, boolean daemon) {}

  @Test
  @DisplayName("AC-APP-10: a run longer than the interval never overlaps the next")
  void runs_now_then_with_a_fixed_delay_without_overlap() throws InterruptedException {
    List<Run> runs = new CopyOnWriteArrayList<>();
    CountDownLatch threeRuns = new CountDownLatch(3);
    Duration delay = Duration.ofMillis(20);
    long scheduledAt = System.nanoTime();

    try (ExecutorJobTimer timer = new ExecutorJobTimer()) {
      timer.runNowThenEvery(
          delay,
          () -> {
            long start = System.nanoTime();
            sleep(Duration.ofMillis(60)); // longer than the delay
            runs.add(new Run(start, System.nanoTime(), Thread.currentThread().isDaemon()));
            threeRuns.countDown();
          });

      assertThat(threeRuns.await(5, TimeUnit.SECONDS)).as("three runs").isTrue();
    }

    assertThat(runs.getFirst().startNanos() - scheduledAt)
        .as("the first run starts at once")
        .isLessThan(Duration.ofMillis(500).toNanos());
    for (int i = 1; i < 3; i++) {
      assertThat(runs.get(i).startNanos() - runs.get(i - 1).endNanos())
          .as("gap between run %d's end and run %d's start", i - 1, i)
          .isGreaterThanOrEqualTo(delay.toNanos());
    }
    assertThat(runs).allMatch(Run::daemon, "runs on a daemon thread");
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
