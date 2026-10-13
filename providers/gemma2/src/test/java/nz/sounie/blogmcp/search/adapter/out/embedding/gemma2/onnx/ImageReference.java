package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Core's {@code gemma2-image-reference.json}: Google's official embeddinggemma-2 weights in fp32
 * PyTorch over the six fixtures and six queries (ADR 0011 Parity). Test data, read with real
 * Jackson.
 */
final class ImageReference {

  static final String RESOURCE = "/gemma2-image-reference.json";

  /** One fixture's preprocessing results and official embedding. */
  record Image(
      String file,
      int width,
      int height,
      long[] inputIds,
      int patches,
      int realPatches,
      int columns,
      int rows,
      int imageTokens,
      double pixelValuesSum,
      float[] embedding) {

    /** The fixture decoded from the test classpath. */
    ImagePixels pixels() {
      return FixtureImages.decode(file);
    }

    /** The file name, so parameterised test names stay readable. */
    @Override
    public String toString() {
      return file;
    }
  }

  /** One text query (without the query prompt) and its official embedding. */
  record Query(String text, float[] embedding) {}

  private static final class Loaded {
    static final JsonNode ROOT = read();
  }

  private ImageReference() {}

  static List<Image> images() {
    return Loaded.ROOT.path("images").values().stream().map(ImageReference::image).toList();
  }

  static Map<String, Image> imagesByFile() {
    return images().stream().collect(Collectors.toMap(Image::file, Function.identity()));
  }

  static List<Query> queries() {
    return Loaded.ROOT.path("queries").values().stream()
        .map(node -> new Query(node.path("query").asString(), floats(node.path("embedding"))))
        .toList();
  }

  private static Image image(JsonNode node) {
    JsonNode grid = node.path("patchGrid");
    return new Image(
        node.path("file").asString(),
        node.path("width").asInt(),
        node.path("height").asInt(),
        node.path("inputIds").values().stream().mapToLong(JsonNode::asLong).toArray(),
        node.path("patches").asInt(),
        node.path("realPatches").asInt(),
        grid.path(0).asInt(),
        grid.path(1).asInt(),
        node.path("imageTokens").asInt(),
        node.path("pixelValuesSum").asDouble(),
        floats(node.path("embedding")));
  }

  private static float[] floats(JsonNode array) {
    List<JsonNode> values = List.copyOf(array.values());
    float[] result = new float[values.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (float) values.get(i).asDouble();
    }
    return result;
  }

  private static JsonNode read() {
    try (InputStream in = ImageReference.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource " + RESOURCE);
      }
      return JsonMapper.builder().build().readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
