package nz.sounie.blogmcp.search.application.embedding.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-APP-48: the gate refuses new model calls first, then waits a bounded time for the running one.
 * No model is needed: a supplied function blocked on a latch stands in for a native inference.
 * Synchronisation uses latches; fixed waits appear only as short "has not happened yet" checks and
 * generous upper bounds.
 */
class ModelGateTest {

  private static final String MODEL_ID = "test-model@rev";
  private static final Duration GENEROUS = Duration.ofSeconds(5);
  private static final long NOT_YET_MILLIS = 200;
  private static final long LONG_WAIT_SECONDS = 10;

  private final ModelGate gate = new ModelGate(MODEL_ID);
  private final ExecutorService threads = Executors.newCachedThreadPool();
  private final CountDownLatch inferenceStarted = new CountDownLatch(1);
  private final CountDownLatch releaseInference = new CountDownLatch(1);
  private final AtomicBoolean inferenceReturned = new AtomicBoolean();

  @AfterEach
  void releaseAndStopThreads() {
    releaseInference.countDown();
    threads.shutdownNow();
  }

  /** Starts a call on another thread that blocks inside the gate until released. */
  private Future<String> blockedInference() throws InterruptedException {
    Future<String> running =
        threads.submit(
            () ->
                gate.call(
                    () -> {
                      inferenceStarted.countDown();
                      awaitUninterruptibly(releaseInference);
                      inferenceReturned.set(true);
                      return "vector";
                    }));
    assertThat(inferenceStarted.await(GENEROUS.toSeconds(), TimeUnit.SECONDS))
        .as("the blocked call is inside the gate")
        .isTrue();
    return running;
  }

  private Future<EmbedderShutdown> shutDownOnAnotherThread(
      Duration maxWait, AtomicReference<Thread> holder) {
    CountDownLatch assigned = new CountDownLatch(1);
    Future<EmbedderShutdown> result =
        threads.submit(
            () -> {
              holder.set(Thread.currentThread());
              assigned.countDown();
              return gate.shutDown(maxWait);
            });
    awaitUninterruptibly(assigned);
    return result;
  }

  /**
   * Waits until the shutting-down thread is parked with a timeout, i.e. it is waiting for the
   * running call. By the gate's contract it has already started refusing new calls by then.
   */
  private static void awaitWaiting(Thread thread) throws InterruptedException {
    long deadline = System.nanoTime() + GENEROUS.toNanos();
    while (thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertThat(thread.getState()).as("shutDown is waiting").isEqualTo(Thread.State.TIMED_WAITING);
  }

  /** Calls the gate on another thread, so a wrongly blocking gate fails the test, not hangs it. */
  private Throwable failureOfCallOnAnotherThread(AtomicBoolean ran) throws Exception {
    Future<String> call =
        threads.submit(
            () ->
                gate.call(
                    () -> {
                      ran.set(true);
                      return "should not run";
                    }));
    try {
      call.get(GENEROUS.toSeconds(), TimeUnit.SECONDS);
      return null;
    } catch (ExecutionException e) {
      return e.getCause();
    }
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean done = false;
    while (!done) {
      try {
        done = latch.await(LONG_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Test
  @DisplayName("Model gate: an open gate runs the call and returns its result")
  void runs_the_call_and_returns_its_result() {
    assertThat(gate.call(() -> "vector")).isEqualTo("vector");
  }

  @Test
  @DisplayName("Model gate: a failure inside the call becomes EmbedderUnavailable naming the model")
  void turns_a_failing_call_into_embedder_unavailable_naming_the_model() {
    IllegalStateException failure = new IllegalStateException("inference failed");

    assertThatThrownBy(
            () ->
                gate.call(
                    () -> {
                      throw failure;
                    }))
        .isInstanceOf(EmbedderUnavailable.class)
        .hasMessageContaining(MODEL_ID)
        .hasCause(failure);
  }

  @Test
  @DisplayName("Model gate: a model that cannot load (LinkageError) becomes EmbedderUnavailable")
  void turns_a_linkage_error_into_embedder_unavailable() {
    UnsatisfiedLinkError missingNativeLibrary = new UnsatisfiedLinkError("no onnxruntime");

    assertThatThrownBy(
            () ->
                gate.call(
                    () -> {
                      throw missingNativeLibrary;
                    }))
        .isInstanceOf(EmbedderUnavailable.class)
        .hasMessageContaining(MODEL_ID)
        .hasCause(missingNativeLibrary);
  }

  @Test
  @DisplayName("Model gate: a failed call leaves the gate free, so shutDown drains at once")
  void a_failed_call_does_not_hold_the_gate() {
    assertThatThrownBy(
            () ->
                gate.call(
                    () -> {
                      throw new IllegalStateException("inference failed");
                    }))
        .isInstanceOf(EmbedderUnavailable.class);

    long start = System.nanoTime();
    EmbedderShutdown outcome = gate.shutDown(GENEROUS);

    assertThat(outcome).isEqualTo(EmbedderShutdown.DRAINED);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("Model gate: calls run one at a time")
  void a_second_call_waits_until_the_running_call_returns() throws Exception {
    Future<String> first = blockedInference();
    CountDownLatch secondStarted = new CountDownLatch(1);
    Future<String> second =
        threads.submit(
            () ->
                gate.call(
                    () -> {
                      secondStarted.countDown();
                      return "second";
                    }));

    assertThat(secondStarted.await(NOT_YET_MILLIS, TimeUnit.MILLISECONDS))
        .as("the second call has not started while the first is running")
        .isFalse();

    releaseInference.countDown();

    assertThat(first.get(GENEROUS.toSeconds(), TimeUnit.SECONDS)).isEqualTo("vector");
    assertThat(second.get(GENEROUS.toSeconds(), TimeUnit.SECONDS)).isEqualTo("second");
  }

  @Test
  @DisplayName(
      "AC-APP-48: shutDown returns DRAINED after the running call has returned, not before")
  void drains_only_after_the_running_call_returns() throws Exception {
    Future<String> running = blockedInference();
    AtomicReference<Thread> shutdownThread = new AtomicReference<>();
    CountDownLatch shutdownStarted = new CountDownLatch(1);
    AtomicReference<Boolean> returnedWhenDrained = new AtomicReference<>();
    Future<EmbedderShutdown> shutdown =
        threads.submit(
            () -> {
              shutdownThread.set(Thread.currentThread());
              shutdownStarted.countDown();
              EmbedderShutdown outcome = gate.shutDown(GENEROUS);
              returnedWhenDrained.set(inferenceReturned.get());
              return outcome;
            });

    awaitUninterruptibly(shutdownStarted);
    awaitWaiting(shutdownThread.get());
    assertThat(shutdown).as("shutDown waits while the call is running").isNotDone();

    releaseInference.countDown();

    assertThat(shutdown.get(GENEROUS.toSeconds(), TimeUnit.SECONDS))
        .isEqualTo(EmbedderShutdown.DRAINED);
    assertThat(returnedWhenDrained.get()).as("the call had returned before DRAINED").isTrue();
    assertThat(running.get(GENEROUS.toSeconds(), TimeUnit.SECONDS)).isEqualTo("vector");
  }

  @Test
  @DisplayName("AC-APP-48: a call started after shutDown began is refused without running")
  void refuses_a_call_started_after_shutdown_began() throws Exception {
    blockedInference();
    AtomicReference<Thread> shutdownThread = new AtomicReference<>();
    Future<EmbedderShutdown> shutdown = shutDownOnAnotherThread(GENEROUS, shutdownThread);
    awaitWaiting(shutdownThread.get());
    AtomicBoolean ran = new AtomicBoolean();

    Throwable failure = failureOfCallOnAnotherThread(ran);

    assertThat(failure).isInstanceOf(EmbedderUnavailable.class);
    assertThat(ran).as("the refused call's function never ran").isFalse();
    releaseInference.countDown();
    assertThat(shutdown.get(GENEROUS.toSeconds(), TimeUnit.SECONDS))
        .isEqualTo(EmbedderShutdown.DRAINED);
  }

  @Test
  @DisplayName(
      "AC-APP-48: a running call that outlasts the wait gives TIMED_OUT once it has passed")
  void times_out_when_the_running_call_outlasts_the_wait() throws Exception {
    blockedInference();
    Duration shortWait = Duration.ofMillis(200);

    long start = System.nanoTime();
    EmbedderShutdown outcome = gate.shutDown(shortWait);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(outcome).isEqualTo(EmbedderShutdown.TIMED_OUT);
    assertThat(elapsed).isGreaterThanOrEqualTo(shortWait).isLessThan(GENEROUS);
  }

  @Test
  @DisplayName("AC-APP-48: after TIMED_OUT, new calls are still refused")
  void refuses_new_calls_after_timing_out() throws Exception {
    blockedInference();
    assertThat(gate.shutDown(Duration.ofMillis(100))).isEqualTo(EmbedderShutdown.TIMED_OUT);
    AtomicBoolean ran = new AtomicBoolean();

    Throwable failure = failureOfCallOnAnotherThread(ran);

    assertThat(failure).isInstanceOf(EmbedderUnavailable.class);
    assertThat(ran).isFalse();
  }

  @Test
  @DisplayName("AC-APP-48: an idle gate drains at once")
  void an_idle_gate_drains_at_once() {
    long start = System.nanoTime();
    EmbedderShutdown outcome = gate.shutDown(GENEROUS);

    assertThat(outcome).isEqualTo(EmbedderShutdown.DRAINED);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("AC-APP-48: a second shutDown also returns DRAINED")
  void a_second_shutdown_also_drains() {
    gate.shutDown(GENEROUS);

    long start = System.nanoTime();
    EmbedderShutdown second = gate.shutDown(GENEROUS);

    assertThat(second).isEqualTo(EmbedderShutdown.DRAINED);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("AC-APP-48: once drained, a call is refused without running its function")
  void refuses_calls_once_drained() {
    gate.shutDown(GENEROUS);
    AtomicBoolean ran = new AtomicBoolean();

    assertThatThrownBy(
            () ->
                gate.call(
                    () -> {
                      ran.set(true);
                      return "should not run";
                    }))
        .isInstanceOf(EmbedderUnavailable.class);
    assertThat(ran).isFalse();
  }
}
