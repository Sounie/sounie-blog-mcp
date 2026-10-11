package nz.sounie.blogmcp.app;

import static nz.sounie.blogmcp.app.mcp.McpResults.isError;
import static nz.sounie.blogmcp.app.mcp.McpResults.post;
import static nz.sounie.blogmcp.app.mcp.McpResults.request;
import static nz.sounie.blogmcp.app.mcp.McpResults.results;
import static nz.sounie.blogmcp.app.mcp.McpResults.structured;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_DEFINITION;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.app.mcp.BlogMcpTools;
import nz.sounie.blogmcp.app.mcp.SiteChoices;
import nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource;
import nz.sounie.blogmcp.catalog.adapter.out.FixedSiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC-APP-11: tool calls during a sync see consistent data. */
class ConcurrentToolCallsTest {

  private static final int POSTS = 20;
  private static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");

  @TempDir Path dataDirectory;

  private final PrintStream errors =
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
  private final FakeBlogSource wordPress = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);

  /** Version A has title "A n" and body "alpha n …"; version B "B n" and "beta n …". */
  private static FakeBlogSource.Step version(String letter, String word, Instant updatedAt) {
    return new FakeBlogSource.Page(
        IntStream.rangeClosed(1, POSTS)
            .mapToObj(
                n ->
                    (SourceEntry)
                        new SourceEntry.Available(
                            aSnapshot()
                                .sourcePostId(String.valueOf(n))
                                .title(letter + " " + n)
                                .body(word + " " + n + " records in java")
                                .publishedAt(Instant.parse("2026-09-01T00:00:00Z"))
                                .updatedAt(updatedAt)
                                .build()))
            .toList());
  }

  @Test
  @DisplayName("AC-APP-11: concurrent calls during a sync never fail and never see a mixed post")
  void calls_during_a_sync_see_the_old_or_the_new_version() throws Exception {
    Wiring wiring =
        Wiring.assemble(
            dataDirectory,
            FixedSiteDirectory.of(SOUNIE_WP_DEFINITION),
            new Adapters(
                Map.of(
                    Platform.WORDPRESS,
                    wordPress,
                    Platform.BLOGGER,
                    new FakeBlogSource(ChangeOrder.NEWEST_FIRST)),
                new FakeEmbedder(),
                new FakeEmbedder(),
                FakeTokenCounter.perWord(1),
                Clock.fixed(NOW, ZoneOffset.UTC)),
            errors);
    wordPress.willServe(SOUNIE_WP_ID, version("A", "alpha", Instant.parse("2026-09-01T00:00:00Z")));
    wiring.job().run();
    wordPress.willServe(SOUNIE_WP_ID, version("B", "beta", Instant.parse("2026-09-30T00:00:00Z")));
    BlogMcpTools tools =
        new BlogMcpTools(
            wiring.searchPosts(), wiring.getPost(), SiteChoices.of(List.of("sounie-wp")), errors);

    CountDownLatch start = new CountDownLatch(1);
    List<Callable<CallToolResult>> calls = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String postId = "sounie-wp:" + (1 + i % POSTS);
      calls.add(
          () -> {
            start.await();
            return tools
                .searchPosts()
                .callHandler()
                .apply(null, request("search_posts", Map.of("query", "records in java")));
          });
      calls.add(
          () -> {
            start.await();
            return tools
                .getPost()
                .callHandler()
                .apply(null, request("get_post", Map.of("post", postId)));
          });
    }

    ExecutorService pool = Executors.newFixedThreadPool(41);
    try {
      Future<?> sync =
          pool.submit(
              () -> {
                awaitQuietly(start);
                wiring.job().run();
              });
      List<Future<CallToolResult>> answers = calls.stream().map(pool::submit).toList();
      start.countDown();

      sync.get(30, TimeUnit.SECONDS);
      for (Future<CallToolResult> answer : answers) {
        CallToolResult result = answer.get(30, TimeUnit.SECONDS);
        assertThat(isError(result)).as("tool error").isFalse();
        assertConsistent(result);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private static void assertConsistent(CallToolResult result) {
    Map<String, Object> structured = structured(result);
    if (structured.containsKey("results")) {
      assertThat(results(result)).isNotEmpty();
      return;
    }
    assertThat(structured).containsEntry("found", true);
    Map<String, Object> post = post(result);
    String title = (String) post.get("title");
    String body = (String) post.get("body");
    assertThat(title.startsWith("A ") ? body.startsWith("alpha ") : body.startsWith("beta "))
        .as("title %s matches body %s", title, body)
        .isTrue();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
