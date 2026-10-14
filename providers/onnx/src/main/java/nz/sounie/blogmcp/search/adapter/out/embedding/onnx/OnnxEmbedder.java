package nz.sounie.blogmcp.search.adapter.out.embedding.onnx;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbedderShutdown;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.application.embedding.spi.ModelGate;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageFormatter;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * The local BGE-small-en-v1.5 (quantised) model through LangChain4j. The only class that touches
 * LangChain4j. Runs its model calls through one {@link ModelGate}; loads the model lazily on first
 * use.
 *
 * <p>The model is one per process (it is about 34 MB and its ONNX session is shared), so the gate
 * is too. One gate call covers a whole {@code embedAll}. LangChain4j's own parallelism within one
 * {@code embedAll} call is left alone (ADR 0005).
 */
public final class OnnxEmbedder implements EmbeddingProvider {

  public static final String MODEL_ID = "bge-small-en-v1.5-q";
  private static final String MODEL_REVISION = "onnx-bge-small-en-v1.5-q-v1";
  private static final PassageTokenBudget BUDGET = new PassageTokenBudget(400, 100, 64, 465, 512);

  /** The per-process gate, like the model. */
  private static final ModelGate SHARED_GATE = new ModelGate(MODEL_ID);

  /**
   * The per-process worker pool that LangChain4j's {@code embedAll} runs on. It is ours, so a
   * shutdown can wait for workers that are still inside ONNX Runtime after the calling thread has
   * left the gate (e.g. interrupted).
   */
  private static final ThreadPoolExecutor SHARED_WORKERS = newWorkerPool();

  private final BgeTokenCounter tokens = new BgeTokenCounter();
  private final ModelGate gate;
  private final ExecutorService workers;
  private final Supplier<EmbeddingModel> model;

  /** Loaded by the JVM on first access to {@link #INSTANCE}, so the model loads lazily. */
  private static final class Model {
    static final EmbeddingModel INSTANCE = new BgeSmallEnV15QuantizedEmbeddingModel(SHARED_WORKERS);

    private Model() {}
  }

  /**
   * The production provider: the per-process gate, worker pool and lazily loaded model. The model
   * is looked up inside each gate call, so it loads under the gate; a failure to load it ({@link
   * LinkageError}, e.g. a missing native library) or to run it becomes {@link
   * nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable}.
   */
  public OnnxEmbedder() {
    this(SHARED_GATE, SHARED_WORKERS, () -> Model.INSTANCE);
  }

  /**
   * A provider with its own gate and worker pool, and its own model loaded now on those workers;
   * for tests that shut a provider down without closing the per-process one.
   */
  OnnxEmbedder(ModelGate gate, ExecutorService workers) {
    this(gate, workers, loadedOn(workers));
  }

  private OnnxEmbedder(ModelGate gate, ExecutorService workers, Supplier<EmbeddingModel> model) {
    this.gate = gate;
    this.workers = workers;
    this.model = model;
  }

  private static Supplier<EmbeddingModel> loadedOn(ExecutorService workers) {
    EmbeddingModel loaded = new BgeSmallEnV15QuantizedEmbeddingModel(workers);
    return () -> loaded;
  }

  /**
   * Daemon workers sized like LangChain4j's default pool: one per processor, idle ones time out.
   */
  static ThreadPoolExecutor newWorkerPool() {
    int threads = Runtime.getRuntime().availableProcessors();
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            threads,
            threads,
            1,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            Thread.ofPlatform().daemon().name("onnx-embedding-", 0).factory());
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  @Override
  public String modelId() {
    return MODEL_ID;
  }

  @Override
  public EmbeddingProviderProfile profile() {
    return new EmbeddingProviderProfile(
        MODEL_REVISION, MODEL_REVISION, MODEL_REVISION, 384, 512, BUDGET, tokens);
  }

  @Override
  public TokenCounter tokenCounter() {
    return tokens;
  }

  @Override
  public String prepareQuery(QueryText query) {
    return EmbeddingInputPreparation.onnxQuery(query);
  }

  @Override
  public String preparePassage(String title, Passage passage) {
    return PassageFormatter.standard().format(title, passage.text()).text();
  }

  /** The version recorded before ADR 0010; keeping it keeps existing ONNX index entries valid. */
  @Override
  public String passagePreparationVersion() {
    return "onnx-bge-document-v1";
  }

  @Override
  public Optional<ImageEmbedder> imageEmbedder() {
    return Optional.empty();
  }

  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    // LangChain4j rejects an empty list; no passages simply need no embeddings.
    return passages.isEmpty() ? List.of() : gate.call(() -> embedAll(model.get(), passages));
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return gate.call(() -> toEmbedding(model.get().embed(query.text()).content()));
  }

  private static List<Embedding> embedAll(EmbeddingModel model, List<Passage> passages) {
    List<TextSegment> segments = passages.stream().map(p -> TextSegment.from(p.text())).toList();
    return model.embedAll(segments).content().stream().map(OnnxEmbedder::toEmbedding).toList();
  }

  private static Embedding toEmbedding(dev.langchain4j.data.embedding.Embedding embedding) {
    return new Embedding(embedding.dimension(), embedding.vector());
  }

  /**
   * Shuts the gate (refuse new calls, wait for the running one), then stops the worker pool and
   * waits for the rest of {@code maxWait} for its workers to leave ONNX Runtime. Workers can
   * outlive the gate call when its thread is interrupted inside {@code embedAll}. Segments still
   * queued are dropped, so the wait is at most one inference per worker. Never loads the model.
   */
  @Override
  public EmbedderShutdown shutDown(Duration maxWait) {
    long deadline = System.nanoTime() + maxWait.toNanos();
    EmbedderShutdown gateOutcome = gate.shutDown(maxWait);
    workers.shutdownNow();
    return workersStoppedBy(deadline) ? gateOutcome : EmbedderShutdown.TIMED_OUT;
  }

  private boolean workersStoppedBy(long deadline) {
    try {
      return workers.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
