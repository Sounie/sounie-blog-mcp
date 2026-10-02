package nz.sounie.blogmcp.search.adapter.out;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import nz.sounie.blogmcp.search.domain.Embedder;
import nz.sounie.blogmcp.search.domain.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.Embedding;
import nz.sounie.blogmcp.search.domain.Passage;
import nz.sounie.blogmcp.search.domain.QueryPassage;

/**
 * The local BGE-small-en-v1.5 (quantised) model through LangChain4j. The only class that touches
 * LangChain4j. Serialises its public calls with one lock; loads the model lazily on first use.
 *
 * <p>The model is one per process (it is about 34 MB and its ONNX session is shared), so the lock
 * is too. LangChain4j's own parallelism within one {@code embedAll} call is left alone (ADR 0005).
 */
public final class OnnxEmbedder implements Embedder {

  public static final String MODEL_ID = "bge-small-en-v1.5-q";

  private static final ReentrantLock MODEL_LOCK = new ReentrantLock();

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
  public List<Embedding> embedPassages(List<Passage> passages) {
    // LangChain4j rejects an empty list; no passages simply need no embeddings.
    return passages.isEmpty() ? List.of() : withModel(model -> embedAll(model, passages));
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return withModel(model -> toEmbedding(model.embed(query.text()).content()));
  }

  private static List<Embedding> embedAll(EmbeddingModel model, List<Passage> passages) {
    List<TextSegment> segments = passages.stream().map(p -> TextSegment.from(p.text())).toList();
    return model.embedAll(segments).content().stream().map(OnnxEmbedder::toEmbedding).toList();
  }

  private static Embedding toEmbedding(dev.langchain4j.data.embedding.Embedding embedding) {
    return new Embedding(embedding.vector());
  }

  /**
   * Runs one call on the model under the model lock. A failure to load the model ({@link
   * LinkageError}, e.g. a missing native library) or to run it becomes {@link EmbedderUnavailable}.
   */
  private static <T> T withModel(Function<EmbeddingModel, T> call) {
    MODEL_LOCK.lock();
    try {
      return call.apply(Model.INSTANCE);
    } catch (RuntimeException | LinkageError e) {
      throw new EmbedderUnavailable("The local embedding model " + MODEL_ID + " failed", e);
    } finally {
      MODEL_LOCK.unlock();
    }
  }
}
