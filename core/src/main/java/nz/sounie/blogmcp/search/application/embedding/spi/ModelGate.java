package nz.sounie.blogmcp.search.application.embedding.spi;

import java.time.Duration;
import java.util.function.Supplier;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;

/**
 * Runs an in-process provider's native model calls one at a time, and owns the shutdown order:
 * refuse new calls first, then wait for the running one (search glossary: "Model gate"). A failure
 * inside a call becomes {@link EmbedderUnavailable} naming the model. JDK-only.
 */
public final class ModelGate {

  /**
   * @param modelId the model named in {@link EmbedderUnavailable} messages
   */
  public ModelGate(String modelId) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /**
   * Runs {@code modelCall} once no other call is running.
   *
   * @throws EmbedderUnavailable if the gate is shut down (without running {@code modelCall}), or if
   *     {@code modelCall} fails to load or run the model
   */
  public <T> T call(Supplier<T> modelCall) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /**
   * Refuses every later call, then waits up to {@code maxWait} for the running call to finish. Safe
   * to call more than once.
   */
  public EmbedderShutdown shutDown(Duration maxWait) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
