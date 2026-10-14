package nz.sounie.blogmcp.search.adapter.out.embedding.onnx;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

  /**
   * The model is looked up inside each gate call, so it loads lazily and under the gate; a failure
   * to load it ({@link LinkageError}, e.g. a missing native library) or to run it becomes {@link
   * nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable}.
   */
  private static final ModelGate GATE = new ModelGate(MODEL_ID);

  private final BgeTokenCounter tokens = new BgeTokenCounter();

  /** Loaded by the JVM on first access to {@link #INSTANCE}, so the model loads lazily. */
  private static final class Model {
    static final EmbeddingModel INSTANCE = new BgeSmallEnV15QuantizedEmbeddingModel();

    private Model() {}
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
    return passages.isEmpty() ? List.of() : GATE.call(() -> embedAll(Model.INSTANCE, passages));
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return GATE.call(() -> toEmbedding(Model.INSTANCE.embed(query.text()).content()));
  }

  private static List<Embedding> embedAll(EmbeddingModel model, List<Passage> passages) {
    List<TextSegment> segments = passages.stream().map(p -> TextSegment.from(p.text())).toList();
    return model.embedAll(segments).content().stream().map(OnnxEmbedder::toEmbedding).toList();
  }

  private static Embedding toEmbedding(dev.langchain4j.data.embedding.Embedding embedding) {
    return new Embedding(embedding.dimension(), embedding.vector());
  }

  /** Never loads the model: the gate only refuses new calls and waits for the running one. */
  @Override
  public EmbedderShutdown shutDown(Duration maxWait) {
    return GATE.shutDown(maxWait);
  }
}
