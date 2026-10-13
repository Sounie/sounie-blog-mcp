package nz.sounie.blogmcp.search.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.node.ObjectNode;

class IndexFileLoaderTest {

  private static final String MODEL = "bge-small-en-v1.5-q";
  private static final String RECIPE = MODEL + "/w300-t400-o50-oc100-c64/tt64-p1";

  private final IndexFileLoader loader = new IndexFileLoader(MODEL);

  private static final IndexedPost POST =
      indexed(
          aPost()
              .id("sounie-wp:1")
              .title("Records in Java — ü")
              .tags("Java", "gRPC")
              .publishedAt(Instant.parse("2024-03-31T11:30:00Z"))
              .updatedAt(Instant.parse("2024-04-01T09:00:00.5Z"))
              .build(),
          fingerprint('a'),
          TestEmbeddings.irregular(1),
          TestEmbeddings.irregular(2));

  /** Two chunks and two images, with indexes 0 and 2 (image 1 was skipped). */
  private static final IndexedPost POST_WITH_IMAGES =
      IndexedPost.restore(
          POST.id(),
          POST.metadata(),
          POST.fingerprint(),
          POST.chunks(),
          List.of(
              new IndexedImage(
                  0, URI.create("https://x.example/swan.jpg"), "Black swan", irregular(3)),
              new IndexedImage(2, URI.create("https://x.example/b.png"), "Grapes", irregular(4))));

  private static Embedding irregular(long seed) {
    return TestEmbeddings.irregular(seed);
  }

  private static ObjectNode validFile() {
    return IndexFileJson.of(POST, MODEL, RECIPE);
  }

  private static ObjectNode validFormatTwoFile() {
    return IndexFileJson.formatTwo(POST_WITH_IMAGES, MODEL, RECIPE);
  }

  @Test
  @DisplayName("AC-SRCH-74: a format-2 file is loaded with its images, bit-exactly")
  void loads_a_format_two_file_with_images() {
    IndexFileLoad load = loader.read(IndexFileJson.bytes(validFormatTwoFile()));

    assertThat(load).isInstanceOf(IndexFileLoad.Loaded.class);
    IndexedPost loaded = ((IndexFileLoad.Loaded) load).post();
    assertThat(loaded).usingRecursiveComparison().isEqualTo(POST_WITH_IMAGES);
    assertThat(loaded.images()).extracting(IndexedImage::index).containsExactly(0, 2);
    assertThat(TestEmbeddings.rawBits(loaded.images().get(1).embedding()))
        .containsExactly(TestEmbeddings.rawBits(POST_WITH_IMAGES.images().get(1).embedding()));
  }

  @Test
  @DisplayName("AC-SRCH-74: a format-1 file (no images array) loads with no images")
  void loads_a_format_one_file_without_images() {
    IndexFileLoad load = loader.read(IndexFileJson.bytes(validFile()));

    assertThat(load)
        .isInstanceOfSatisfying(
            IndexFileLoad.Loaded.class, loaded -> assertThat(loaded.post().images()).isEmpty());
  }

  static Stream<Arguments> formatTwoCorruptions() {
    return Stream.of(
        Arguments.of(
            "image vector of the wrong dimension",
            change(
                file ->
                    IndexFileJson.image(file, 0).put("vector", IndexFileJson.vector(ones(383))))),
        Arguments.of(
            "image indexes out of order",
            change(file -> IndexFileJson.image(file, 1).put("index", 0))),
        Arguments.of(
            "image URL not https",
            change(file -> IndexFileJson.image(file, 0).put("url", "http://x.example/swan.jpg"))),
        Arguments.of("format 3", change(file -> file.put("format", 3))),
        Arguments.of("format 0", change(file -> file.put("format", 0))));
  }

  private static byte[] change(Consumer<ObjectNode> change) {
    ObjectNode file = validFormatTwoFile();
    change.accept(file);
    return IndexFileJson.bytes(file);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formatTwoCorruptions")
  @DisplayName("AC-SRCH-74: another format, or a broken image, makes the file unreadable")
  void broken_format_two_file_is_unreadable(String kind, byte[] content) {
    assertUnreadable(loader.read(content));
  }

  @Test
  @DisplayName("AC-APP-25: a valid file built with the current model is loaded exactly")
  void loads_a_valid_file() {
    IndexFileLoad load = loader.read(IndexFileJson.bytes(validFile()));

    assertThat(load).isInstanceOf(IndexFileLoad.Loaded.class);
    IndexedPost loaded = ((IndexFileLoad.Loaded) load).post();
    assertThat(loaded).usingRecursiveComparison().isEqualTo(POST);
    assertThat(TestEmbeddings.rawBits(loaded.chunks().get(1).embedding()))
        .containsExactly(TestEmbeddings.rawBits(POST.chunks().get(1).embedding()));
  }

  @Test
  @DisplayName("AC-APP-25: a post with zero chunks is loaded")
  void loads_a_post_without_chunks() {
    IndexedPost empty = indexed(aPost().id("elegant:9").build(), fingerprint('b'));

    IndexFileLoad load = loader.read(IndexFileJson.bytes(IndexFileJson.of(empty, MODEL, RECIPE)));

    assertThat(load)
        .isInstanceOfSatisfying(
            IndexFileLoad.Loaded.class,
            loaded -> assertThat(loaded.post()).usingRecursiveComparison().isEqualTo(empty));
  }

  @Test
  @DisplayName("AC-APP-29: a file built with another model is incompatible, not loaded")
  void file_from_another_model_is_incompatible() {
    IndexFileLoad load =
        loader.read(IndexFileJson.bytes(IndexFileJson.of(POST, "another-model", "another/r")));

    assertThat(load).isEqualTo(new IndexFileLoad.IncompatibleModel("another-model"));
  }

  @Test
  @DisplayName(
      "Review #9: another model's file is incompatible even when its vectors differ in length")
  void file_from_another_model_with_other_dimension_is_incompatible_not_unreadable() {
    ObjectNode file = IndexFileJson.of(POST, "another-model", "another/r");
    IndexFileJson.chunk(file, 0).put("vector", IndexFileJson.vector(ones(768)));

    IndexFileLoad load = loader.read(IndexFileJson.bytes(file));

    assertThat(load).isEqualTo(new IndexFileLoad.IncompatibleModel("another-model"));
  }

  @Test
  @DisplayName(
      "AC-APP-29: a different recipe with the same model still loads (the fingerprint decides)")
  void file_with_another_recipe_of_the_same_model_loads() {
    IndexFileLoad load =
        loader.read(IndexFileJson.bytes(IndexFileJson.of(POST, MODEL, MODEL + "/w200")));

    assertThat(load).isInstanceOf(IndexFileLoad.Loaded.class);
  }

  static Stream<Arguments> corruptions() {
    return Stream.of(
        corrupt("bad base64", file -> IndexFileJson.chunk(file, 0).put("vector", "not*base64!")),
        corrupt(
            "vector of the wrong length",
            file -> IndexFileJson.chunk(file, 0).put("vector", IndexFileJson.vector(ones(383)))),
        corrupt("gap in chunk indexes", file -> IndexFileJson.chunk(file, 1).put("index", 2)),
        corrupt(
            "metadata site differs from post ID",
            file -> ((ObjectNode) file.get("metadata")).put("siteId", "elegant")),
        corrupt("unknown format", file -> file.put("format", 3)),
        corrupt("missing fingerprint", file -> file.remove("fingerprint")),
        corrupt("missing model ID (S2)", file -> file.remove("modelId")),
        corrupt("malformed post ID", file -> file.put("postId", "no-separator")),
        corrupt(
            "invalid instant",
            file -> ((ObjectNode) file.get("metadata")).put("publishedAt", "soon")));
  }

  private static Arguments corrupt(String kind, Consumer<ObjectNode> change) {
    ObjectNode file = validFile();
    change.accept(file);
    return Arguments.of(kind, IndexFileJson.bytes(file));
  }

  private static float[] ones(int length) {
    float[] values = new float[length];
    Arrays.fill(values, 1f);
    return values;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corruptions")
  @DisplayName("AC-APP-28: a file that breaks a format or domain rule is unreadable")
  void corrupt_file_is_unreadable(String kind, byte[] content) {
    assertUnreadable(loader.read(content));
  }

  @Test
  @DisplayName("AC-APP-28: text that is not JSON, or is truncated, is unreadable")
  void non_json_and_truncated_files_are_unreadable() {
    byte[] valid = IndexFileJson.bytes(validFile());

    assertUnreadable(loader.read("not json".getBytes(UTF_8)));
    assertUnreadable(loader.read(Arrays.copyOf(valid, valid.length / 2)));
  }

  private static void assertUnreadable(IndexFileLoad load) {
    assertThat(load)
        .isInstanceOfSatisfying(
            IndexFileLoad.Unreadable.class,
            unreadable -> assertThat(unreadable.reason()).isNotBlank());
  }
}
