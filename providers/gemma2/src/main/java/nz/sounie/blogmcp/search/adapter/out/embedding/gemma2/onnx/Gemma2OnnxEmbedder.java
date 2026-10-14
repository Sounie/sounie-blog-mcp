package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2InputPreparation;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2Profile;
import nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.Gemma2TokenCounter;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbedderShutdown;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.application.embedding.spi.ModelGate;
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
 * 0010). Runs its model calls through one {@link ModelGate}; loads the model lazily on first use.
 */
public final class Gemma2OnnxEmbedder implements EmbeddingProvider {

  private static final String MODEL_REVISION = "daa72c51243991dfcaf9f9137d2c573d8f7790c0";
  private static final String MODEL_ID = "onnx-community/embeddinggemma-2-ONNX@daa72c51-q8";
  private static final String PROFILE_KEY = "gemma2-onnx-q8-daa72c51-v1";

  /**
   * One per process, like the model. The model is looked up inside each gate call, so it loads
   * lazily and under the gate.
   */
  private static final ModelGate GATE = new ModelGate(MODEL_ID);

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
   * Preprocesses outside the gate (pure Java), then runs the vision encoder and the text model in
   * one gate call. Images take no task prefix.
   */
  private static Embedding embedImage(ImagePixels pixels) {
    VisionInput input = preprocess(pixels);
    long[] inputIds = ImageInputIds.of(input.grid());
    return GATE.call(
        () ->
            EmbeddingFactory.createGemma2Embedding(
                Model.INSTANCE.embedImage(inputIds, Vision.INSTANCE.features(input))));
  }

  /**
   * Preprocesses outside the model gate. A failure, such as an image too large to resize, becomes
   * {@link EmbedderUnavailable} (AC-SRCH-65).
   */
  private static VisionInput preprocess(ImagePixels pixels) {
    try {
      return Gemma2ImagePreprocessor.preprocess(pixels);
    } catch (RuntimeException e) {
      throw new EmbedderUnavailable("Could not preprocess " + pixels + " for " + MODEL_ID, e);
    }
  }

  /**
   * One gate call per passage, so the longest a shutdown waits is one inference (app.md 3.4). A
   * post cut off mid-way fails as a whole.
   */
  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    return passages.stream().map(passage -> embed(passage.text())).toList();
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return embed(query.text());
  }

  private static Embedding embed(String preparedText) {
    return GATE.call(
        () ->
            EmbeddingFactory.createGemma2Embedding(
                Model.INSTANCE.embed(Tokenizer.INSTANCE.modelInputIds(preparedText))));
  }

  /** Never loads the model: the gate only refuses new calls and waits for the running one. */
  @Override
  public EmbedderShutdown shutDown(Duration maxWait) {
    return GATE.shutDown(maxWait);
  }
}
