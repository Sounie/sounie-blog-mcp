package nz.sounie.blogmcp.app.mcp;

import static nz.sounie.blogmcp.app.mcp.McpResults.isError;
import static nz.sounie.blogmcp.app.mcp.McpResults.post;
import static nz.sounie.blogmcp.app.mcp.McpResults.request;
import static nz.sounie.blogmcp.app.mcp.McpResults.results;
import static nz.sounie.blogmcp.app.mcp.McpResults.structured;
import static nz.sounie.blogmcp.app.mcp.McpResults.text;
import static nz.sounie.blogmcp.app.mcp.McpResults.textAsJson;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.application.GetPost;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.PostReference;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import nz.sounie.blogmcp.search.adapter.out.InMemoryQueryImageSource;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import nz.sounie.blogmcp.search.application.FindPostsLikeImage;
import nz.sounie.blogmcp.search.application.SearchPosts;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.index.ContentFingerprint;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Words;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Handler-level tests of the two tools: real SDK request and result types, real use cases, fakes of
 * our own ports only (app.md section 6).
 */
class BlogMcpToolsTest {

  private static final ContentFingerprint FINGERPRINT = new ContentFingerprint("a".repeat(64));
  private static final String HELLO_URL = "https://blog2.sounie.nz/2026/09/20/hello/";

  private final InMemoryVectorIndex index = new InMemoryVectorIndex();
  private final FakeEmbedder embedder = new FakeEmbedder().answerQueriesWith(Vectors.query());
  private final BreakablePostRepository posts = new BreakablePostRepository();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final BlogMcpTools tools =
      new BlogMcpTools(
          new SearchPosts(index, embedder),
          new GetPost(posts),
          SiteChoices.of(List.of("sounie-wp", "elegant")),
          new PrintStream(stderr, true, StandardCharsets.UTF_8));

  private CallToolResult searchPosts(Map<String, Object> arguments) {
    return tools.searchPosts().callHandler().apply(null, request("search_posts", arguments));
  }

  private CallToolResult getPost(String reference) {
    return tools
        .getPost()
        .callHandler()
        .apply(null, request("get_post", Map.of("post", reference)));
  }

  private String stderr() {
    return stderr.toString(StandardCharsets.UTF_8);
  }

  /** An indexed post whose chunk {@code i} has the given text and similarity to the query. */
  private void givenIndexed(String postId, Instant publishedAt, String title, Chunk... chunks) {
    PostId id = PostId.parse(postId);
    PostMetadata metadata =
        new PostMetadata(
            id.siteId(),
            "https://example.org/" + id.sourcePostId(),
            title,
            Set.of(),
            publishedAt,
            publishedAt);
    List<IndexedChunk> indexed =
        IntStream.range(0, chunks.length)
            .mapToObj(i -> new IndexedChunk(i, chunks[i].text(), chunks[i].embedding()))
            .toList();
    index.save(IndexedPost.restore(id, metadata, FINGERPRINT, indexed));
  }

  /** An indexed post with one chunk and one image. */
  private void givenIndexedWithImage(
      String postId, Instant publishedAt, String title, Chunk chunk, IndexedImage image) {
    PostId id = PostId.parse(postId);
    PostMetadata metadata =
        new PostMetadata(
            id.siteId(),
            "https://example.org/" + id.sourcePostId(),
            title,
            Set.of(),
            publishedAt,
            publishedAt);
    index.save(
        IndexedPost.restore(
            id,
            metadata,
            FINGERPRINT,
            List.of(new IndexedChunk(0, chunk.text(), chunk.embedding())),
            List.of(image)));
  }

  private record Chunk(String text, Embedding embedding) {}

  private static Chunk chunk(String text, double similarity) {
    return new Chunk(text, Vectors.atSimilarity(384, similarity));
  }

  @Nested
  class SearchPostsTool {

    @Test
    @DisplayName("AC-APP-2: ranked results with snippets, filtered by site and NZ dates")
    void returns_ranked_results_with_snippets() {
      givenIndexed(
          "elegant:1",
          Instant.parse("2024-03-31T11:30:00Z"), // 00:30 NZDT on 1 April
          "Records in Java",
          chunk(Words.numbered(300), 0.9),
          chunk("a short second chunk", 0.5));
      givenIndexed("elegant:4", Instant.parse("2024-05-01T00:00:00Z"), "Later", chunk("x", 0.7));
      givenIndexed(
          "elegant:2", Instant.parse("2024-03-31T10:30:00Z"), "Too early", chunk("y", 0.95));
      givenIndexed(
          "sounie-wp:3", Instant.parse("2024-06-01T00:00:00Z"), "Other site", chunk("z", 0.99));

      CallToolResult result =
          searchPosts(
              Map.of(
                  "query", "records in java",
                  "site", "elegant",
                  "from", "2024-04-01",
                  "to", "2024-12-31",
                  "limit", 5));

      assertThat(isError(result)).isFalse();
      assertThat(embedder.queries())
          .containsExactly(QueryPassage.of(new QueryText("records in java")));
      List<Map<String, Object>> results = results(result);
      assertThat(results)
          .extracting(r -> r.get("postId"))
          .containsExactly("elegant:1", "elegant:4");
      assertThat(results.getFirst())
          .isEqualTo(
              Map.of(
                  "postId", "elegant:1",
                  "title", "Records in Java",
                  "url", "https://example.org/1",
                  "site", "elegant",
                  "published", "2024-04-01",
                  "score", 0.9,
                  "snippet", Words.numbered(60) + " …"));
      assertThat(textAsJson(result)).isEqualTo(structured(result));
    }

    @Test
    @DisplayName(
        "AC-SRCH-75: an image match shows matchedImage; a chunk match has no such key; both"
            + " validate against the output schema")
    void an_image_match_shows_the_matched_image() {
      givenIndexedWithImage(
          "elegant:1",
          Instant.parse("2024-05-01T00:00:00Z"),
          "Swans",
          chunk("about birds", 0.6),
          new IndexedImage(
              0,
              URI.create("https://x.example/swan.jpg"),
              "Black swan",
              Vectors.atSimilarity(384, 0.9)));
      givenIndexed(
          "elegant:2", Instant.parse("2024-05-01T00:00:00Z"), "Records", chunk("records", 0.8));

      CallToolResult result = searchPosts(Map.of("query", "black swan"));

      assertThat(isError(result)).isFalse();
      List<Map<String, Object>> results = results(result);
      assertThat(results)
          .extracting(r -> r.get("postId"))
          .containsExactly("elegant:1", "elegant:2");
      assertThat(results.get(0))
          .containsEntry("matchedImage", "https://x.example/swan.jpg")
          .containsEntry("snippet", "Black swan");
      assertThat(results.get(1)).doesNotContainKey("matchedImage");
      assertThat(textAsJson(result)).isEqualTo(structured(result));

      Map<String, Object> outputSchema = tools.searchPosts().tool().outputSchema();
      Map<String, Object> item =
          object(object(object(outputSchema, "properties"), "results"), "items");
      assertThat(object(object(item, "properties"), "matchedImage"))
          .containsEntry("type", "string")
          .containsEntry("format", "uri");
      assertThat(item.get("required"))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .doesNotContain("matchedImage");
      assertThat(
              McpJsonDefaults.getSchemaValidator()
                  .validate(outputSchema, result.structuredContent())
                  .valid())
          .as("the structured content validates against the output schema")
          .isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> parent, String key) {
      return (Map<String, Object>) parent.get(key);
    }

    @Test
    @DisplayName("AC-APP-2: filters that exclude every post give an empty list, not an error")
    void no_matching_posts_is_an_empty_list() {
      givenIndexed("elegant:1", Instant.parse("2024-05-01T00:00:00Z"), "Records", chunk("x", 0.9));

      CallToolResult result = searchPosts(Map.of("query", "gradle", "site", "sounie-wp"));

      assertThat(isError(result)).isFalse();
      assertThat(results(result)).isEmpty();
      assertThat(textAsJson(result)).isEqualTo(structured(result));
    }

    @Test
    @DisplayName("AC-APP-2: an empty index gives an empty list")
    void an_empty_index_is_an_empty_list() {
      CallToolResult result = searchPosts(Map.of("query", "gradle"));

      assertThat(isError(result)).isFalse();
      assertThat(results(result)).isEmpty();
    }

    static Stream<Arguments> invalidArguments() {
      return Stream.of(
          Arguments.of(Map.of(), List.of("query")),
          Arguments.of(Map.of("query", "   "), List.of("query")),
          Arguments.of(
              Map.of("query", "x", "from", "2024-12-31", "to", "2024-04-01"),
              List.of("from", "to")),
          Arguments.of(Map.of("query", "x", "from", "01/04/2024"), List.of("from", "01/04/2024")),
          Arguments.of(Map.of("query", "x", "limit", "ten"), List.of("limit")),
          Arguments.of(
              Map.of("query", "x", "site", "nope"),
              List.of("site", "nope", "sounie-wp", "elegant")),
          Arguments.of(Map.of("query", "x", "date_from", "2024-01-01"), List.of("date_from")));
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    @DisplayName("AC-APP-3: invalid arguments are tool errors naming the argument and the problem")
    void invalid_arguments_are_tool_errors(Map<String, Object> arguments, List<String> named) {
      CallToolResult result = searchPosts(new HashMap<>(arguments));

      assertThat(isError(result)).isTrue();
      named.forEach(fragment -> assertThat(text(result)).containsIgnoringCase(fragment));
      assertThat(isError(searchPosts(Map.of("query", "gradle"))))
          .as("the next valid call succeeds")
          .isFalse();
    }

    @Test
    @DisplayName(
        "AC-APP-6: a failing local model is a tool error, logged once, and search recovers")
    void a_failing_model_is_a_tool_error_and_the_next_call_is_answered() {
      embedder.failingQueries();

      CallToolResult failed = searchPosts(Map.of("query", "gradle"));

      assertThat(isError(failed)).isTrue();
      assertThat(text(failed))
          .contains("search is temporarily unavailable because the local model failed");
      assertThat(stderr().lines()).hasSize(1);

      embedder.working();
      assertThat(isError(searchPosts(Map.of("query", "gradle")))).isFalse();
    }
  }

  /** AC-APP-51 to 53: the image tool, over the real use case and our query-image-source fake. */
  @Nested
  class FindPostsLikeImageTool {

    private static final ImagePixels QUERY_PIXELS = InMemoryImageSource.pixels(9);
    private static final String SWAN_URL = "https://x.example/swan.jpg";

    @TempDir Path dir;

    private final InMemoryQueryImageSource queryImages = new InMemoryQueryImageSource();
    private final BlogMcpTools imageTools =
        tools.withImageSearch(new FindPostsLikeImage(index, embedder, queryImages));
    private Path swan;

    @BeforeEach
    void givenAQueryImageFile() throws IOException {
      embedder.assignImage(QUERY_PIXELS, Vectors.query());
      swan = Files.write(dir.resolve("swan.png"), new byte[100]);
      queryImages.reading(swan, QUERY_PIXELS);
    }

    private SyncToolSpecification findTool(BlogMcpTools from) {
      return from.specifications().stream()
          .filter(spec -> spec.tool().name().equals("find_posts_like_image"))
          .findFirst()
          .orElseThrow();
    }

    private CallToolResult findPostsLikeImage(Map<String, Object> arguments) {
      return findTool(imageTools)
          .callHandler()
          .apply(null, request("find_posts_like_image", arguments));
    }

    private void assertUseCaseNotCalled() {
      assertThat(queryImages.filesRead()).as("files read").isEmpty();
      assertThat(queryImages.urlsFetched()).as("URLs fetched").isEmpty();
      assertThat(embedder.imageCalls()).as("images embedded").isEmpty();
    }

    @Test
    @DisplayName(
        "AC-APP-51: an image-capable provider offers search_posts, get_post and"
            + " find_posts_like_image")
    void an_image_capable_provider_offers_three_tools() {
      assertThat(imageTools.specifications())
          .extracting(spec -> spec.tool().name())
          .containsExactly("search_posts", "get_post", "find_posts_like_image");
    }

    @Test
    @DisplayName("AC-APP-51 (AC-APP-1): a text-only provider offers exactly the two tools")
    void a_text_only_provider_offers_two_tools() {
      assertThat(tools.specifications())
          .extracting(spec -> spec.tool().name())
          .containsExactly("search_posts", "get_post");
    }

    @Test
    @DisplayName("AC-APP-51: the image tool's title, description and annotations (app.md 3.9)")
    void the_image_tool_is_described() {
      Tool tool = findTool(imageTools).tool();

      assertThat(tool.title()).isEqualTo("Find the owner's blog posts like an image");
      assertThat(tool.description())
          .isEqualTo(
              "Finds the owner's blog posts (sites: sounie-wp, elegant) whose text or images are"
                  + " most like a given image. `image` is an absolute path to a local JPEG, PNG"
                  + " or GIF file of at most 10 MB, or an https URL of one. Returns up to `limit`"
                  + " posts ranked by similarity, best first, each with a short snippet,"
                  + " `matchedImage` when the post's best match is one of its images, and a"
                  + " `postId` for `get_post`. Scores are only comparable within one result"
                  + " list. "
                  + ToolDefinitions.CONTENT_NOTICE);
      ToolAnnotations annotations = tool.annotations();
      assertThat(annotations.title()).isEqualTo(tool.title());
      assertThat(annotations.readOnlyHint()).isTrue();
      assertThat(annotations.destructiveHint()).isFalse();
      assertThat(annotations.idempotentHint()).isTrue();
      assertThat(annotations.openWorldHint()).as("it may fetch a URL").isTrue();
      assertThat(tools.searchPosts().tool().annotations().openWorldHint()).isFalse();
    }

    @Test
    @DisplayName(
        "AC-APP-51: the image tool requires image, shares search_posts' scope arguments and"
            + " output schema")
    void the_image_tool_schemas() {
      Tool tool = findTool(imageTools).tool();
      Tool search = tools.searchPosts().tool();
      Map<String, Object> properties = object(tool.inputSchema(), "properties");
      Map<String, Object> searchProperties = object(search.inputSchema(), "properties");

      assertThat(tool.inputSchema())
          .containsEntry("type", "object")
          .containsEntry("additionalProperties", false)
          .containsEntry("required", List.of("image"));
      assertThat(properties).containsOnlyKeys("image", "site", "from", "to", "limit");
      assertThat(object(properties, "image"))
          .isEqualTo(
              Map.of(
                  "type",
                  "string",
                  "minLength",
                  1,
                  "description",
                  "Absolute path to a local .jpg, .jpeg, .png or .gif file (at most 10 MB), or"
                      + " an https URL of a JPEG, PNG or GIF image."));
      List.of("site", "from", "to", "limit")
          .forEach(
              name ->
                  assertThat(properties.get(name))
                      .as("%s as in search_posts", name)
                      .isEqualTo(searchProperties.get(name)));
      assertThat(object(properties, "site").get("enum")).isEqualTo(List.of("sounie-wp", "elegant"));
      assertThat(tool.outputSchema()).isEqualTo(search.outputSchema());
    }

    @Test
    @DisplayName(
        "AC-APP-52: a ranked outcome is answered in the search_posts shape, with matchedImage")
    void a_ranking_is_answered_like_search_posts() {
      givenIndexedWithImage(
          "elegant:1",
          Instant.parse("2024-05-01T00:00:00Z"),
          "Swans",
          chunk("about birds", 0.4),
          new IndexedImage(
              0, URI.create(SWAN_URL), "Black swan", Vectors.atSimilarity(384, 0.9, 2)));
      givenIndexed(
          "elegant:2", Instant.parse("2024-06-01T00:00:00Z"), "Reeds", chunk("reeds", 0.7));
      givenIndexed(
          "sounie-wp:3", Instant.parse("2024-06-01T00:00:00Z"), "Other site", chunk("z", 0.99));

      CallToolResult result =
          findPostsLikeImage(
              Map.of(
                  "image", swan.toString(),
                  "site", "elegant",
                  "from", "2024-04-01",
                  "to", "2024-12-31",
                  "limit", 5));

      assertThat(isError(result)).isFalse();
      assertThat(queryImages.filesRead()).containsExactly(swan);
      List<Map<String, Object>> results = results(result);
      assertThat(results)
          .extracting(r -> r.get("postId"))
          .containsExactly("elegant:1", "elegant:2");
      assertThat(results.get(0))
          .containsEntry("matchedImage", SWAN_URL)
          .containsEntry("snippet", "Black swan")
          .containsEntry("score", 0.9);
      assertThat(results.get(1)).doesNotContainKey("matchedImage");
      assertThat(textAsJson(result)).isEqualTo(structured(result));
      assertThat(
              McpJsonDefaults.getSchemaValidator()
                  .validate(findTool(imageTools).tool().outputSchema(), result.structuredContent())
                  .valid())
          .as("the structured content validates against the output schema")
          .isTrue();
    }

    @Test
    @DisplayName("AC-APP-52: an empty ranking is an empty list, not an error")
    void an_empty_ranking_is_an_empty_list() {
      CallToolResult result = findPostsLikeImage(Map.of("image", swan.toString()));

      assertThat(isError(result)).isFalse();
      assertThat(results(result)).isEmpty();
    }

    static Stream<Arguments> invalidArguments() {
      return Stream.of(
          Arguments.of(Map.of(), List.of("image")),
          Arguments.of(Map.of("image", "swan.png"), List.of("swan.png")),
          Arguments.of(
              Map.of("image", "<d>/missing.png"), List.of("no readable file at", "missing.png")),
          Arguments.of(
              Map.of("image", "http://example.com/a.png"), List.of("http://example.com/a.png")),
          Arguments.of(
              Map.of("image", "<d>/swan.png", "site", "nope"),
              List.of("site", "nope", "sounie-wp", "elegant")),
          Arguments.of(
              Map.of("image", "<d>/swan.png", "from", "2024-12-31", "to", "2024-04-01"),
              List.of("from", "to")),
          Arguments.of(Map.of("image", "<d>/swan.png", "query", "swans"), List.of("query")));
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    @DisplayName(
        "AC-APP-52: invalid arguments are tool errors naming the problem; the use case is not"
            + " called")
    void invalid_arguments_are_tool_errors(Map<String, Object> arguments, List<String> named) {
      Map<String, Object> inDir = new HashMap<>();
      arguments.forEach(
          (name, value) -> inDir.put(name, value.toString().replace("<d>", dir.toString())));

      CallToolResult result = findPostsLikeImage(inDir);

      assertThat(isError(result)).isTrue();
      named.forEach(fragment -> assertThat(text(result)).containsIgnoringCase(fragment));
      assertUseCaseNotCalled();
      assertThat(isError(findPostsLikeImage(Map.of("image", swan.toString()))))
          .as("the next valid call succeeds")
          .isFalse();
    }

    @Test
    @DisplayName("AC-APP-53: a refused WebP image is a tool error that says why")
    void a_refused_image_is_a_tool_error() {
      queryImages.rejecting("https://images.example.com/a.webp", ImageRejection.UNSUPPORTED_TYPE);

      CallToolResult result =
          findPostsLikeImage(Map.of("image", "https://images.example.com/a.webp"));

      assertThat(isError(result)).isTrue();
      assertThat(text(result))
          .isEqualTo(
              "Cannot use image 'https://images.example.com/a.webp': it is not a JPEG, PNG or"
                  + " GIF image (WebP is not supported)");
    }

    @ParameterizedTest
    @EnumSource(
        value = ImageRejection.class,
        names = {"TOO_SMALL", "TOO_LARGE"})
    @DisplayName("AC-APP-53: a too small or too large image gets its explanation")
    void a_refusal_carries_its_explanation(ImageRejection reason) {
      String url = "https://images.example.com/" + reason.name().toLowerCase() + ".png";
      queryImages.rejecting(url, reason);

      CallToolResult result = findPostsLikeImage(Map.of("image", url));

      assertThat(isError(result)).isTrue();
      assertThat(text(result)).isEqualTo("Cannot use image '" + url + "': " + reason.explanation());
    }

    @Test
    @DisplayName(
        "AC-APP-53: an unavailable image is a tool error with the detail, quoting the trimmed"
            + " argument")
    void an_unavailable_image_is_a_tool_error() {
      queryImages.unavailable("https://slow.example/a.png", "connect timed out");

      CallToolResult result = findPostsLikeImage(Map.of("image", "  https://slow.example/a.png "));

      assertThat(isError(result)).isTrue();
      assertThat(text(result))
          .isEqualTo("Could not load image 'https://slow.example/a.png': connect timed out");
    }

    @Test
    @DisplayName("AC-APP-53 (AC-APP-6): a failing image embedder is the model-failed tool error")
    void a_failing_image_embedder_is_the_model_failed_error() {
      embedder.failingForImage(QUERY_PIXELS);

      CallToolResult result = findPostsLikeImage(Map.of("image", swan.toString()));

      assertThat(isError(result)).isTrue();
      assertThat(text(result))
          .isEqualTo("search is temporarily unavailable because the local model failed");
      assertThat(stderr().lines()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> parent, String key) {
      return (Map<String, Object>) parent.get(key);
    }
  }

  @Nested
  class GetPostTool {

    private static final Instant PUBLISHED = Instant.parse("2024-03-31T11:30:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-21T08:00:00Z");

    private void givenHelloPost() {
      posts.save(
          aSnapshot()
              .sourcePostId("123")
              .url(HELLO_URL)
              .title("Hello")
              .body("Hello, world. The whole body, not a snippet.")
              .tags("DDD", "Java")
              .publishedAt(PUBLISHED)
              .updatedAt(UPDATED)
              .buildStoredPost());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sounie-wp:123", "http://BLOG2.sounie.nz/2026/09/20/hello#comments"})
    @DisplayName("AC-APP-4: the full post by ID or by URL")
    void returns_the_full_post_by_id_or_url(String reference) {
      givenHelloPost();

      CallToolResult result = getPost(reference);

      assertThat(isError(result)).isFalse();
      assertThat(structured(result)).containsEntry("found", true);
      assertThat(post(result))
          .isEqualTo(
              Map.of(
                  "postId", "sounie-wp:123",
                  "title", "Hello",
                  "url", HELLO_URL,
                  "site", "sounie-wp",
                  "published", "2024-04-01",
                  "publishedAt", "2024-03-31T11:30:00Z",
                  "updatedAt", "2026-09-21T08:00:00Z",
                  "tags", List.of("DDD", "Java"),
                  "completeness", "FULL",
                  "body", "Hello, world. The whole body, not a snippet."));
      assertThat(textAsJson(result)).isEqualTo(structured(result));
    }

    @Test
    @DisplayName("AC-APP-4: a SUMMARY post is returned with its summary as the body")
    void returns_a_summary_post() {
      posts.save(
          aSnapshot()
              .sourcePostId("124")
              .body("Only the summary.")
              .completeness(BodyCompleteness.SUMMARY)
              .buildStoredPost());

      CallToolResult result = getPost("sounie-wp:124");

      assertThat(isError(result)).isFalse();
      assertThat(post(result))
          .containsEntry("completeness", "SUMMARY")
          .containsEntry("body", "Only the summary.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sounie-wp:999", "https://blog2.sounie.nz/2026/09/20/nope/"})
    @DisplayName("AC-APP-5: an unknown post is a normal 'not found' answer naming the reference")
    void an_unknown_post_is_not_found(String reference) {
      givenHelloPost();

      CallToolResult result = getPost(reference);

      assertThat(isError(result)).isFalse();
      assertThat(structured(result)).containsEntry("found", false);
      assertThat((String) structured(result).get("message")).contains(reference);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-an-id"})
    @DisplayName(
        "AC-APP-5: a malformed reference is a tool error with the InvalidPostReference message")
    void a_malformed_reference_is_a_tool_error(String reference) {
      String expected = catchThrowable(() -> PostReference.parse(reference)).getMessage();

      CallToolResult result = getPost(reference);

      assertThat(isError(result)).isTrue();
      assertThat(text(result)).isEqualTo(expected);
    }

    @Test
    @DisplayName(
        "AC-APP-6: an unexpected failure is a generic tool error; the trace goes to stderr")
    void an_unexpected_failure_is_a_generic_tool_error_and_the_next_call_is_answered() {
      givenHelloPost();
      posts.breakWith(new IllegalStateException("disk on fire"));

      CallToolResult failed = getPost("sounie-wp:123");

      assertThat(isError(failed)).isTrue();
      assertThat(text(failed)).doesNotContain("disk on fire").doesNotContain("\tat ");
      assertThat(stderr()).contains("disk on fire").contains("\tat ");

      posts.repair();
      CallToolResult next = getPost("sounie-wp:123");
      assertThat(isError(next)).isFalse();
      assertThat(structured(next)).containsEntry("found", true);
    }
  }
}
