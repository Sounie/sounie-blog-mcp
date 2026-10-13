package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The q8 EmbeddingGemma 2 text model in one ONNX Runtime session (ADR 0010 spike results). Takes
 * model input IDs and returns the graph's {@code sentence_embedding}, which is already pooled,
 * projected to 768 values and normalised. The graph also requires image, video and audio features:
 * text calls pass them all empty, and image calls pass the vision encoder's image features (ADR
 * 0011).
 */
final class Gemma2OnnxModel {

  private static final String RESOURCES = "/embeddinggemma-2-onnx";
  private static final String GRAPH = "model_quantized.onnx";
  private static final List<String> FILES = List.of(GRAPH, "model_quantized.onnx_data");
  private static final String INPUT_IDS = "input_ids";
  private static final String ATTENTION_MASK = "attention_mask";
  private static final String IMAGE_INPUT = "image_features";
  private static final List<String> MEDIA_INPUTS =
      List.of(IMAGE_INPUT, "video_features", "audio_features");

  /** The media this provider never sends; always passed empty. */
  private static final List<String> NO_MEDIA_INPUTS = MEDIA_INPUTS.subList(1, 3);

  private static final int FEATURE_WIDTH = 512;
  private static final float[][] NO_IMAGE = new float[0][];
  private static final String OUTPUT = "sentence_embedding";

  private final OrtEnvironment environment;
  private final OrtSession session;

  private Gemma2OnnxModel(OrtEnvironment environment, OrtSession session) {
    this.environment = environment;
    this.session = session;
  }

  /**
   * Extracts the bundled files and opens a CPU session on them.
   *
   * @throws IllegalStateException if the model cannot be loaded or declares unexpected inputs
   */
  static Gemma2OnnxModel load() {
    var directory = BundledModelFiles.extract(Gemma2OnnxModel.class, RESOURCES, FILES);
    try {
      OrtEnvironment environment = OrtEnvironment.getEnvironment();
      environment.setTelemetry(false);
      OrtSession session =
          environment.createSession(
              directory.resolve(GRAPH).toString(), new OrtSession.SessionOptions());
      requireKnownInputs(session.getInputNames());
      return new Gemma2OnnxModel(environment, session);
    } catch (OrtException e) {
      throw new IllegalStateException("Cannot load the bundled EmbeddingGemma 2 model", e);
    }
  }

  private static void requireKnownInputs(Set<String> declared) {
    List<String> unknown =
        declared.stream()
            .filter(name -> !INPUT_IDS.equals(name) && !ATTENTION_MASK.equals(name))
            .filter(name -> !MEDIA_INPUTS.contains(name))
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalStateException("The model declares unexpected inputs " + unknown);
    }
  }

  /** The sentence embedding for one text input sequence (batch size 1), with no media. */
  float[] embed(long[] inputIds) {
    return embed(inputIds, NO_IMAGE);
  }

  /**
   * The sentence embedding for one image: its input ids, whose image soft tokens the graph replaces
   * with the vision encoder's features, one row per token. Video and audio stay empty.
   */
  float[] embedImage(long[] inputIds, float[][] imageFeatures) {
    return embed(inputIds, imageFeatures);
  }

  private float[] embed(long[] inputIds, float[][] imageFeatures) {
    Map<String, OnnxTensor> inputs = new HashMap<>();
    try {
      long[] mask = new long[inputIds.length];
      Arrays.fill(mask, 1L);
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(environment, new long[][] {inputIds}));
      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(environment, new long[][] {mask}));
      inputs.put(IMAGE_INPUT, media(imageFeatures));
      for (String media : NO_MEDIA_INPUTS) {
        inputs.put(media, media(NO_IMAGE));
      }
      try (OrtSession.Result result = session.run(inputs)) {
        return ((float[][]) result.get(OUTPUT).orElseThrow().getValue())[0];
      }
    } catch (OrtException e) {
      throw new IllegalStateException("The EmbeddingGemma 2 model failed to run", e);
    } finally {
      inputs.values().forEach(OnnxTensor::close);
    }
  }

  /** A {@code [rows, 512]} media-features tensor; zero rows when there is no such media. */
  private OnnxTensor media(float[][] rows) throws OrtException {
    FloatBuffer values = FloatBuffer.allocate(rows.length * FEATURE_WIDTH);
    Arrays.stream(rows).forEach(values::put);
    return OnnxTensor.createTensor(
        environment, values.rewind(), new long[] {rows.length, FEATURE_WIDTH});
  }
}
