package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.server;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2InputPreparation;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2TokenCounter;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.EmbeddingFactory;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * Trying out Google DeepMind's Gemma 2 embedding model, running locally behind an OpenAI-compatible
 * API.
 */
public final class Gemma2Embedder implements EmbeddingProvider {

  private static final String MODEL_ID = "text-embedding-embeddinggemma-2";
  private static final String REVISION = "914f7f89142e33e77833254d9c9b90c3cef7303b";
  private static final String PROFILE_KEY = "gemma2-914f7f89-v1";
  private static final PassageTokenBudget BUDGET =
      new PassageTokenBudget(3000, 256, 128, 4096, 8192);
  private static final ReentrantLock MODEL_LOCK = new ReentrantLock();

  private static final class Tokenizer {
    static final Gemma2TokenCounter INSTANCE = new Gemma2TokenCounter();
  }

  /** Loaded by the JVM on first access to {@link #INSTANCE}, so the model loads lazily. */
  private static final class Model {
    // This is a local server that proxies to the real Gemma 2 model, so we can run tests without an
    // API key.
    static final EmbeddingModel INSTANCE =
        OpenAiEmbeddingModel.builder()
            .baseUrl(
                Optional.ofNullable(System.getenv("BLOG_MCP_EMBEDDING_ENDPOINT"))
                    .filter(value -> !value.isBlank())
                    .orElse("http://localhost:1234/v1"))
            .modelName(MODEL_ID)
            .build();

    private Model() {}
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    Response<dev.langchain4j.data.embedding.Embedding> response =
        Model.INSTANCE.embed(query.text());

    return toEmbedding(response.content());
  }

  @Override
  public String modelId() {
    return MODEL_ID;
  }

  @Override
  public EmbeddingProviderProfile profile() {
    Gemma2TokenCounter tokenizer = Tokenizer.INSTANCE;
    return new EmbeddingProviderProfile(
        REVISION, REVISION, PROFILE_KEY, 768, 8192, BUDGET, tokenizer);
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
    String prepared = Gemma2InputPreparation.gemma2Passage(title, passage.text());
    int tokens = Tokenizer.INSTANCE.countTokens(prepared);
    if (tokens > BUDGET.preparedPassageTokens()) {
      throw new IllegalArgumentException(
          "Prepared Gemma2 passage has "
              + tokens
              + " tokens, exceeding the "
              + BUDGET.preparedPassageTokens()
              + "-token budget");
    }
    return prepared;
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
