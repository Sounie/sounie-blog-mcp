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

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import nz.sounie.blogmcp.search.application.SearchPosts;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.index.ContentFingerprint;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Words;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
