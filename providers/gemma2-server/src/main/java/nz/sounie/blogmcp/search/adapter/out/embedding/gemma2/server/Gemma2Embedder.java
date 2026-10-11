package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.server;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2InputPreparation;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2Profile;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2TokenCounter;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.EmbeddingFactory;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * Google EmbeddingGemma 2 served by a local OpenAI-compatible endpoint such as LM Studio (ADR 0009,
 * ADR 0010). Serialises its public calls with one lock.
 */
public final class Gemma2Embedder implements EmbeddingProvider {

  private static final String MODEL_ID = "text-embedding-embeddinggemma-2";
  private static final String PROFILE_KEY = "gemma2-914f7f89-v1";
  private static final String DEFAULT_ENDPOINT = "http://localhost:1234/v1";

  /**
   * Review #11: long enough for a 4,096-token passage on a local model, short enough that a stalled
   * server fails a search well within an MCP client's patience. No retries: the next search or
   * scheduled sync tries again.
   */
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final ReentrantLock MODEL_LOCK = new ReentrantLock();

  private static final class Tokenizer {
    static final Gemma2TokenCounter INSTANCE = new Gemma2TokenCounter();
  }

  private final EmbeddingModel model;

  /** The endpoint from {@code BLOG_MCP_EMBEDDING_ENDPOINT}, or LM Studio's default. */
  public Gemma2Embedder() {
    this(
        Optional.ofNullable(System.getenv("BLOG_MCP_EMBEDDING_ENDPOINT"))
            .filter(value -> !value.isBlank())
            .orElse(DEFAULT_ENDPOINT),
        TIMEOUT);
  }

  Gemma2Embedder(String endpoint, Duration timeout) {
    // A local server needs no API key.
    this.model =
        OpenAiEmbeddingModel.builder()
            .baseUrl(endpoint)
            .modelName(MODEL_ID)
            .timeout(timeout)
            .maxRetries(0)
            .build();
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return withModel(model -> toEmbedding(model.embed(query.text()).content()));
  }

  @Override
  public String modelId() {
    return MODEL_ID;
  }

  @Override
  public EmbeddingProviderProfile profile() {
    // The server's model is the Google revision itself, so it shares the tokenizer's revision.
    return Gemma2Profile.of(Gemma2Profile.TOKENIZER_REVISION, PROFILE_KEY, Tokenizer.INSTANCE);
  }

  @Override
  public TokenCounter tokenCounter() {
    return Tokenizer.INSTANCE;
  }

  @Override
  public String prepareQuery(QueryText query) {
    return Gemma2InputPreparation.gemma2Query(query);
  }

  @Override
  public String preparePassage(String title, Passage passage) {
    return Gemma2Profile.preparePassage(title, passage, Tokenizer.INSTANCE);
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
  private <T> T withModel(Function<EmbeddingModel, T> call) {
    MODEL_LOCK.lock();
    try {
      return call.apply(model);
    } catch (RuntimeException | LinkageError e) {
      throw new EmbedderUnavailable("The local embedding model " + MODEL_ID + " failed", e);
    } finally {
      MODEL_LOCK.unlock();
    }
  }
}
