package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The real bundled q8 vision encoder and text model on ONNX Runtime against the official-weights
 * reference (ADR 0011 Parity); no test doubles and no network. Needs the pinned files, e.g. {@code
 * ./gw :providers:gemma2:test -PhuggingFaceMirror=.hf-mirror}.
 */
@Tag("model")
class Gemma2OnnxImageEmbeddingTest {

  private static final String RESOURCES = "/embeddinggemma-2-onnx/";

  /** ADR 0011 Parity: Java image embeddings vs official. */
  private static final double IMAGE_COSINE_THRESHOLD = 0.994;

  /** ADR 0011 Parity: query embeddings vs official. */
  private static final double QUERY_COSINE_THRESHOLD = 0.999;

  /** AC-SRCH-63's pairs, by file name: the JSON lists queries and images in different orders. */
  private static final Map<String, String> INTENDED_IMAGE =
      Map.of(
          "a black swan swimming on a lake", "black-swan-in-reeds.png",
          "grapes growing on a vine in the garden", "grapes-on-trellis.png",
          "motor racing at a race track", "race-car-at-circuit.png",
          "pub quiz question about sloths", "quiz-slide-sloth.png",
          "rugby match in a stadium at night", "rugby-stadium-smoke.png",
          "managing images in a web service", "image-management-services.jpg");

  private static final Gemma2OnnxEmbedder EMBEDDER = new Gemma2OnnxEmbedder();

  /** The six fixtures embedded once per test JVM, by file name. */
  private static final class JavaImageEmbeddings {
    static final Map<String, Embedding> BY_FILE = embedAll();

    private static Map<String, Embedding> embedAll() {
      ImageEmbedder images = EMBEDDER.imageEmbedder().orElseThrow();
      return ImageReference.images().stream()
          .collect(
              Collectors.toMap(
                  ImageReference.Image::file,
                  reference -> images.embedImage(reference.pixels()),
                  (a, b) -> a,
                  LinkedHashMap::new));
    }
  }

  @Test
  @DisplayName("AC-SRCH-64: the q8 vision encoder and its weights are bundled with the provider")
  void bundles_the_q8_vision_encoder() {
    assertThat(Gemma2OnnxEmbedder.class.getResource(RESOURCES + "vision_encoder_quantized.onnx"))
        .isNotNull();
    assertThat(
            Gemma2OnnxEmbedder.class.getResource(RESOURCES + "vision_encoder_quantized.onnx_data"))
        .isNotNull();
  }

  @Test
  @DisplayName("AC-SRCH-62, 64: image embeddings match the official model, offline")
  void image_embeddings_match_the_official_model() {
    Map<String, Double> cosines = new LinkedHashMap<>();
    for (ImageReference.Image reference : ImageReference.images()) {
      Embedding java = JavaImageEmbeddings.BY_FILE.get(reference.file());
      assertThat(java.values()).as(reference.file()).hasSize(768);
      assertThat(norm(java.values())).as(reference.file()).isCloseTo(1.0, within(1e-3));
      cosines.put(reference.file(), cosine(java.values(), reference.embedding()));
    }

    // AC-SRCH-62: the lowest goes into ADR 0011's Parity row "Image embeddings, Java vs official".
    System.out.println("Image embeddings, Java vs official: " + cosines);
    assertThat(cosines)
        .allSatisfy(
            (file, cosine) ->
                assertThat(cosine).as(file).isGreaterThanOrEqualTo(IMAGE_COSINE_THRESHOLD));
  }

  @Test
  @DisplayName("AC-SRCH-63: each test query ranks its intended image first, 6 of 6")
  void each_query_ranks_its_intended_image_first() {
    Map<String, String> best = new LinkedHashMap<>();
    for (ImageReference.Query reference : ImageReference.queries()) {
      Embedding query = EMBEDDER.embedQuery(query(reference.text()));
      String top =
          JavaImageEmbeddings.BY_FILE.entrySet().stream()
              .max(Comparator.comparingDouble(e -> query.similarityTo(e.getValue()).value()))
              .orElseThrow()
              .getKey();
      best.put(reference.text(), top);
    }

    assertThat(best).containsExactlyInAnyOrderEntriesOf(INTENDED_IMAGE);
  }

  @Test
  @DisplayName("AC-SRCH-65: text embeddings are unchanged and still match the official model")
  void text_query_embeddings_still_match_the_official_model() {
    for (ImageReference.Query reference : ImageReference.queries()) {
      Embedding query = EMBEDDER.embedQuery(query(reference.text()));

      assertThat(cosine(query.values(), reference.embedding()))
          .as(reference.text())
          .isGreaterThanOrEqualTo(QUERY_COSINE_THRESHOLD);
    }
  }

  private static QueryPassage query(String text) {
    return new QueryPassage(EMBEDDER.prepareQuery(new QueryText(text)));
  }

  private static double cosine(float[] a, float[] b) {
    double dot = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
    }
    return dot / (norm(a) * norm(b));
  }

  private static double norm(float[] values) {
    double sum = 0;
    for (float value : values) {
      sum += (double) value * value;
    }
    return Math.sqrt(sum);
  }
}
