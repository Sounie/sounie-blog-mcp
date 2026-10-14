package nz.sounie.blogmcp.search.application.embedding.spi;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;

/**
 * Runs an in-process provider's native model calls one at a time, and owns the shutdown order:
 * refuse new calls first, then wait for the running one (search glossary: "Model gate"). A failure
 * inside a call becomes {@link EmbedderUnavailable} naming the model. JDK-only.
 */
public final class ModelGate {

  private final String modelId;
  private final ReentrantLock running = new ReentrantLock();
  private volatile boolean shutDown;

  /**
   * @param modelId the model named in {@link EmbedderUnavailable} messages
   */
  public ModelGate(String modelId) {
    this.modelId = modelId;
  }

  /**
   * Runs {@code modelCall} once no other call is running.
   *
   * @throws EmbedderUnavailable if the gate is shut down (without running {@code modelCall}), or if
   *     {@code modelCall} fails to load or run the model
   */
  public <T> T call(Supplier<T> modelCall) {
    refuseIfShutDown();
    running.lock();
    try {
      // Checked again: shutDown may have begun while this call waited for the running one.
      refuseIfShutDown();
      return run(modelCall);
    } finally {
      running.unlock();
    }
  }

  /**
   * Refuses every later call, then waits up to {@code maxWait} for the running call to finish. Safe
   * to call more than once.
   */
  public EmbedderShutdown shutDown(Duration maxWait) {
    shutDown = true;
    try {
      return drainedWithin(maxWait);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return EmbedderShutdown.TIMED_OUT;
    }
  }

  private EmbedderShutdown drainedWithin(Duration maxWait) throws InterruptedException {
    boolean idle = running.tryLock(maxWait.toNanos(), TimeUnit.NANOSECONDS);
    return idle ? releasedAsDrained() : EmbedderShutdown.TIMED_OUT;
  }

  private EmbedderShutdown releasedAsDrained() {
    running.unlock();
    return EmbedderShutdown.DRAINED;
  }

  private void refuseIfShutDown() {
    if (shutDown) {
      throw new EmbedderUnavailable("The local embedding model " + modelId + " is shut down");
    }
  }

  private <T> T run(Supplier<T> modelCall) {
    try {
      return modelCall.get();
    } catch (RuntimeException | LinkageError e) {
      throw new EmbedderUnavailable("The local embedding model " + modelId + " failed", e);
    }
  }
}
