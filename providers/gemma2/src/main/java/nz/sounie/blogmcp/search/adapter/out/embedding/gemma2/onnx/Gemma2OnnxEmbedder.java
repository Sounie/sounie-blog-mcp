package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

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
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * Google EmbeddingGemma 2 in process: the bundled q8 ONNX model from {@code
 * onnx-community/embeddinggemma-2-ONNX} on ONNX Runtime, with no model server and no network (ADR
 * 0010). Serialises its public calls with one lock; loads the model lazily on first use.
 */
public final class Gemma2OnnxEmbedder implements EmbeddingProvider {

  private static final String MODEL_REVISION = "daa72c51243991dfcaf9f9137d2c573d8f7790c0";
  private static final String MODEL_ID = "onnx-community/embeddinggemma-2-ONNX@daa72c51-q8";
  private static final String PROFILE_KEY = "gemma2-onnx-q8-daa72c51-v1";
  private static final ReentrantLock MODEL_LOCK = new ReentrantLock();

  private static final class Tokenizer {
    static final Gemma2TokenCounter INSTANCE = new Gemma2TokenCounter();
  }

  /** Loaded by the JVM on first access to {@link #INSTANCE}, so the model loads lazily. */
  private static final class Model {
    static final Gemma2OnnxModel INSTANCE = Gemma2OnnxModel.load();

    private Model() {}
  }

  /**
   * The vision encoder, in its own holder so that only the first image call loads it; text-only
   * processes never do (ADR 0011).
   */
  private static final class Vision {
    static final Gemma2VisionEncoder INSTANCE = Gemma2VisionEncoder.load();

    private Vision() {}
  }

  @Override
  public String modelId() {
    return MODEL_ID;
  }

  @Override
  public EmbeddingProviderProfile profile() {
    return Gemma2Profile.of(MODEL_REVISION, PROFILE_KEY, Tokenizer.INSTANCE);
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
  public String passagePreparationVersion() {
    return Gemma2Profile.PASSAGE_PREPARATION_VERSION;
  }

  @Override
  public Optional<ImageEmbedder> imageEmbedder() {
    return Optional.of(Gemma2OnnxEmbedder::embedImage);
  }

  /**
   * Preprocesses outside the lock (pure Java), then runs the vision encoder and the text model
   * under it. Images take no task prefix.
   */
  private static Embedding embedImage(ImagePixels pixels) {
    VisionInput input = preprocess(pixels);
    long[] inputIds = ImageInputIds.of(input.grid());
    return withModel(
        model ->
            EmbeddingFactory.createGemma2Embedding(
                model.embedImage(inputIds, Vision.INSTANCE.features(input))));
  }

  /**
   * Preprocesses without the model lock. A failure, such as an image too large to resize, becomes
   * {@link EmbedderUnavailable} (AC-SRCH-65).
   */
  private static VisionInput preprocess(ImagePixels pixels) {
    try {
      return Gemma2ImagePreprocessor.preprocess(pixels);
    } catch (RuntimeException e) {
      throw new EmbedderUnavailable("Could not preprocess " + pixels + " for " + MODEL_ID, e);
    }
  }

  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    return withModel(model -> passages.stream().map(p -> embed(model, p.text())).toList());
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return withModel(model -> embed(model, query.text()));
  }

  private static Embedding embed(Gemma2OnnxModel model, String preparedText) {
    return EmbeddingFactory.createGemma2Embedding(
        model.embed(Tokenizer.INSTANCE.modelInputIds(preparedText)));
  }

  /**
   * Runs one call on the model under the model lock. A failure to load the model ({@link
   * LinkageError}, e.g. a missing native library) or to run it becomes {@link EmbedderUnavailable}.
   */
  private static <T> T withModel(Function<Gemma2OnnxModel, T> call) {
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
