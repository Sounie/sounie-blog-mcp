package nz.sounie.blogmcp.search.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;
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

  private static ObjectNode validFile() {
    return IndexFileJson.of(POST, MODEL, RECIPE);
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
        corrupt("unknown format", file -> file.put("format", 2)),
        corrupt("missing fingerprint", file -> file.remove("fingerprint")),
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
