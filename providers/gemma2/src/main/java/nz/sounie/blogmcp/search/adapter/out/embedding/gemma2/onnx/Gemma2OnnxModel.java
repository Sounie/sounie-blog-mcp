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
 * projected to 768 values and normalised. The graph also requires image, video and audio features;
 * text-only calls pass them empty.
 */
final class Gemma2OnnxModel {

  private static final String RESOURCES = "/embeddinggemma-2-onnx";
  private static final String GRAPH = "model_quantized.onnx";
  private static final List<String> FILES = List.of(GRAPH, "model_quantized.onnx_data");
  private static final String INPUT_IDS = "input_ids";
  private static final String ATTENTION_MASK = "attention_mask";
  private static final List<String> MEDIA_INPUTS =
      List.of("image_features", "video_features", "audio_features");
  private static final long[] NO_MEDIA = {0, 512};
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

  /** The sentence embedding for one input sequence (batch size 1). */
  float[] embed(long[] inputIds) {
    Map<String, OnnxTensor> inputs = new HashMap<>();
    try {
      long[] mask = new long[inputIds.length];
      Arrays.fill(mask, 1L);
      inputs.put(INPUT_IDS, OnnxTensor.createTensor(environment, new long[][] {inputIds}));
      inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(environment, new long[][] {mask}));
      for (String media : MEDIA_INPUTS) {
        inputs.put(media, OnnxTensor.createTensor(environment, FloatBuffer.allocate(0), NO_MEDIA));
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
}
