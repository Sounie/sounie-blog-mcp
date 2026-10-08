package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import nz.sounie.blogmcp.search.application.QueryEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.EmbeddingFactory;
import nz.sounie.blogmcp.search.domain.embedding.PassageEmbedder;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.text.Passage;

/** Trying out Google DeepMind's Gemma 2 embedding model, running locally behind an OpenAI-compatible API. */
public class Gemma2Embedder implements PassageEmbedder, QueryEmbedder {

  private static final String MODEL_ID = "text-embedding-embeddinggemma-2";
  private static final ReentrantLock MODEL_LOCK = new ReentrantLock();

  /** Loaded by the JVM on first access to {@link #INSTANCE}, so the model loads lazily. */
  private static final class Model {
    // This is a local server that proxies to the real Gemma 2 model, so we can run tests without an API key.
    static final EmbeddingModel INSTANCE = OpenAiEmbeddingModel.builder()
            .baseUrl("http://localhost:1234/v1")
            .modelName(MODEL_ID)
            .build();

    private Model() {}
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    Response<dev.langchain4j.data.embedding.Embedding> response = Model.INSTANCE.embed(query.text());

    return toEmbedding(response.content());
  }

  @Override
  public String modelId() {
    return "text-embedding-embeddinggemma-2";
  }

  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    // LangChain4j rejects an empty list; no passages simply need no embeddings.
    return passages.isEmpty() ? List.of() : withModel(model -> embedAll(model, passages));
  }

  private static List<Embedding> embedAll(EmbeddingModel model, List<Passage> passages) {
    List<TextSegment> segments = passages.stream().map(p -> TextSegment.from(p.text())).toList();
    return model.embedAll(segments).content().stream().map(Gemma2Embedder::toEmbedding).toList();
  }

  private static Embedding toEmbedding(dev.langchain4j.data.embedding.Embedding embedding) {
    return EmbeddingFactory.createGemma2Embedding(embedding.vector());
  }

  /**
   * Runs one call on the model under the model lock. A failure to load the model ({@link
   * LinkageError}, e.g. a missing native library) or to run it becomes {@link EmbedderUnavailable}.
   */
  private static <T> T withModel(Function<EmbeddingModel, T> call) {
    MODEL_LOCK.lock();
    try {
      return call.apply(Gemma2Embedder.Model.INSTANCE);
    } catch (RuntimeException | LinkageError e) {
      throw new EmbedderUnavailable("The local embedding model " + MODEL_ID + " failed", e);
    } finally {
      MODEL_LOCK.unlock();
    }
  }
}
