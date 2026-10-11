package nz.sounie.blogmcp.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IndexWriteLockTest {

  private final IndexWriteLock lock = new IndexWriteLock();

  @Test
  void returns_the_mutation_result() {
    assertThat(lock.locked(() -> "done")).isEqualTo("done");
  }

  @Test
  void mutations_never_overlap() throws Exception {
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger maxInside = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
      for (int i = 0; i < 4; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return lock.locked(
                      () -> {
                        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        Thread.onSpinWait();
                        return inside.decrementAndGet();
                      });
                }));
      }
      start.countDown();
      for (Future<Integer> result : results) {
        result.get(10, TimeUnit.SECONDS);
      }
    }

    assertThat(maxInside.get()).isEqualTo(1);
  }
}
