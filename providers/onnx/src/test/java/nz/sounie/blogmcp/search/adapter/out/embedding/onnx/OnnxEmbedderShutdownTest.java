package nz.sounie.blogmcp.search.adapter.out.embedding.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbedderShutdown;
import nz.sounie.blogmcp.search.application.embedding.spi.ModelGate;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-APP-48 for the onnx provider, against the real BGE model through LangChain4j: interrupting the
 * job thread inside {@code embedAll} must not let LangChain4j's worker threads outlive the drain.
 * Uses a fresh gate and worker pool, so the shared per-process ones stay open for the other model
 * tests in this JVM.
 */
@Tag("model")
class OnnxEmbedderShutdownTest {

  private static final Duration GENEROUS = Duration.ofSeconds(5);

  private final ThreadPoolExecutor workers = OnnxEmbedder.newWorkerPool();
  private final OnnxEmbedder embedder = new OnnxEmbedder(new ModelGate("fresh-gate"), workers);
  private final ExecutorService job = Executors.newSingleThreadExecutor();

  @AfterEach
  void stopThreads() {
    job.shutdownNow();
    workers.shutdownNow();
  }

  private static List<Passage> manyPassages() {
    String text =
        "Writing build.gradle.kts for a Java project. Apply the java plugin, set the toolchain to"
            + " Java 25, add mavenCentral() as a repository and declare JUnit as a test dependency.";
    return IntStream.range(0, 2_000).mapToObj(i -> new Passage(i + " " + text)).toList();
  }

  private void awaitWorkersEmbedding() throws InterruptedException {
    long deadline = System.nanoTime() + GENEROUS.toNanos();
    while (workers.getActiveCount() == 0 && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertThat(workers.getActiveCount()).as("LangChain4j workers are embedding").isPositive();
  }

  @Test
  @DisplayName(
      "AC-APP-48: after the job thread is interrupted mid-embedAll, shutDown drains the workers")
  void drains_the_worker_threads_after_the_job_thread_is_interrupted() throws Exception {
    Future<?> indexing = job.submit(() -> embedder.embedPassages(manyPassages()));
    awaitWorkersEmbedding();

    indexing.cancel(true);
    EmbedderShutdown outcome = embedder.shutDown(GENEROUS);

    assertThat(outcome).isEqualTo(EmbedderShutdown.DRAINED);
    assertThat(workers.isTerminated()).as("no worker is left inside ONNX Runtime").isTrue();
  }
}
