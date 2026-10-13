package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The q8 {@code vision_encoder_quantized} graph in its own ONNX Runtime session (search.md 2.1).
 * Outputs {@code image_features [n, 512]}, where {@code n} must equal the patch grid's image
 * tokens.
 */
final class Gemma2VisionEncoder {

  private static final String RESOURCES = "/embeddinggemma-2-onnx";
  private static final String GRAPH = "vision_encoder_quantized.onnx";
  private static final List<String> FILES = List.of(GRAPH, "vision_encoder_quantized.onnx_data");
  private static final String PIXEL_VALUES = "pixel_values";
  private static final String POSITION_IDS = "pixel_position_ids";
  private static final String OUTPUT = "image_features";
  private static final long[] PIXEL_SHAPE = {1, Patchifier.MAX_PATCHES, Patchifier.PATCH_VALUES};
  private static final long[] POSITION_SHAPE = {1, Patchifier.MAX_PATCHES, 2};

  private final OrtEnvironment environment;
  private final OrtSession session;

  private Gemma2VisionEncoder(OrtEnvironment environment, OrtSession session) {
    this.environment = environment;
    this.session = session;
  }

  /**
   * Extracts the bundled vision files and opens a CPU session on them.
   *
   * @throws IllegalStateException if the encoder cannot be loaded or declares unexpected inputs
   */
  static Gemma2VisionEncoder load() {
    var directory = BundledModelFiles.extract(Gemma2VisionEncoder.class, RESOURCES, FILES);
    try {
      OrtEnvironment environment = OrtEnvironment.getEnvironment();
      environment.setTelemetry(false);
      OrtSession session =
          environment.createSession(
              directory.resolve(GRAPH).toString(), new OrtSession.SessionOptions());
      requireInputs(session);
      return new Gemma2VisionEncoder(environment, session);
    } catch (OrtException e) {
      throw new IllegalStateException("Cannot load the bundled EmbeddingGemma 2 vision encoder", e);
    }
  }

  private static void requireInputs(OrtSession session) {
    if (!session.getInputNames().equals(Set.of(PIXEL_VALUES, POSITION_IDS))) {
      throw new IllegalStateException(
          "The vision encoder declares unexpected inputs " + session.getInputNames());
    }
  }

  /**
   * The image features for one preprocessed image, one row per image token.
   *
   * @throws IllegalStateException if the encoder fails or returns the wrong number of rows
   */
  float[][] features(VisionInput input) {
    try (OnnxTensor pixels =
            OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(input.pixelValues()), PIXEL_SHAPE);
        OnnxTensor positions =
            OnnxTensor.createTensor(
                environment, LongBuffer.wrap(input.positionIds()), POSITION_SHAPE);
        OrtSession.Result result =
            session.run(Map.of(PIXEL_VALUES, pixels, POSITION_IDS, positions))) {
      return checkedFeatures((float[][]) result.get(OUTPUT).orElseThrow().getValue(), input.grid());
    } catch (OrtException e) {
      throw new IllegalStateException("The EmbeddingGemma 2 vision encoder failed to run", e);
    }
  }

  /**
   * Returns the features if their row count equals the grid's image tokens.
   *
   * @throws IllegalStateException if it does not
   */
  static float[][] checkedFeatures(float[][] imageFeatures, PatchGrid grid) {
    if (imageFeatures.length != grid.imageTokens()) {
      throw new IllegalStateException(
          "The vision encoder returned "
              + imageFeatures.length
              + " feature rows for "
              + grid.imageTokens()
              + " image tokens");
    }
    return imageFeatures;
  }
}
