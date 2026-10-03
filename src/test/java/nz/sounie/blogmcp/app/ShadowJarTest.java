package nz.sounie.blogmcp.app;

import static nz.sounie.blogmcp.app.mcp.McpResults.isError;
import static nz.sounie.blogmcp.app.mcp.McpResults.results;
import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.page;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource;
import nz.sounie.blogmcp.catalog.adapter.out.FixedSiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.catalog.domain.site.Site;
import nz.sounie.blogmcp.catalog.domain.site.SiteDefinition;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.search.adapter.out.BgeTokenCounter;
import nz.sounie.blogmcp.search.adapter.out.OnnxEmbedder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-APP-12: the shadow jar runs as an MCP server under the real client and exits when stdin
 * closes. Runs only in the {@code jarTest} Gradle task, after {@code shadowJar}; uses the real
 * model.
 */
@Tag("jar")
@Tag("model")
class ShadowJarTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(120);
  private static final SiteDefinition UNREACHABLE =
      new SiteDefinition("sounie-wp", "WORDPRESS", "https://localhost:1");

  @TempDir Path dir;

  private static Path jar() {
    String jar = System.getProperty("blogmcp.shadowJar");
    assertThat(jar).as("system property blogmcp.shadowJar (set by the jarTest task)").isNotNull();
    return Path.of(jar);
  }

  private static List<String> jarArguments() {
    List<String> args = new ArrayList<>(ServerProcess.sandboxOptions());
    args.addAll(List.of("-jar", jar().toString()));
    return args;
  }

  private Path config() throws IOException {
    return Files.writeString(
        dir.resolve("sites.json"),
        """
        {"sites": [{"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://localhost:1"}]}
        """);
  }

  /** A fixture post and its index entry, written by the real stack with the real model. */
  private Path dataWithFixturePost() {
    Path data = dir.resolve("data");
    Site site =
        new Site(new SiteId("sounie-wp"), Platform.WORDPRESS, URI.create(UNREACHABLE.baseUrl()));
    FakeBlogSource source = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);
    source.willServe(
        site.id(),
        page(
            new SourceEntry.Available(
                aSnapshot()
                    .on(site)
                    .sourcePostId("1")
                    .url("https://localhost:1/sourdough/")
                    .title("Baking sourdough bread at home")
                    .body(
                        "Sourdough bread needs a lively starter, a long cold fermentation and a"
                            + " very hot oven.")
                    .publishedAt(Instant.parse("2026-09-01T00:00:00Z"))
                    .updatedAt(Instant.parse("2026-09-01T00:00:00Z"))
                    .build())));
    OnnxEmbedder model = new OnnxEmbedder();
    Wiring.assemble(
            data,
            FixedSiteDirectory.of(UNREACHABLE),
            new Adapters(
                Map.of(Platform.WORDPRESS, source, Platform.BLOGGER, source),
                model,
                model,
                new BgeTokenCounter(),
                Clock.systemUTC()),
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
        .job()
        .run();
    return data;
  }

  @Test
  @DisplayName("AC-APP-12: the jar answers a real client from persisted data, with a clean stderr")
  void the_jar_serves_the_fixture_post_to_a_real_client() throws IOException {
    Path config = config();
    Path data = dataWithFixturePost();
    List<String> stderr = new CopyOnWriteArrayList<>();
    StdioClientTransport transport =
        new StdioClientTransport(
            ServerProcess.parameters(jarArguments(), config, data), McpJsonDefaults.getMapper());
    transport.setStdErrorHandler(stderr::add);

    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(TIMEOUT).initializationTimeout(TIMEOUT).build()) {
      client.initialize();
      assertThat(client.listTools().tools())
          .extracting(Tool::name)
          .containsExactlyInAnyOrder("search_posts", "get_post");

      CallToolResult result =
          client.callTool(
              CallToolRequest.builder("search_posts")
                  .arguments(Map.of("query", "how do I bake sourdough bread"))
                  .build());

      assertThat(isError(result)).isFalse();
      assertThat(results(result))
          .first()
          .satisfies(r -> assertThat(r).containsEntry("postId", "sounie-wp:1"));
    }

    String errors = String.join("\n", stderr);
    assertThat(errors)
        .doesNotContainIgnoringCase("SLF4J")
        .doesNotContain("restricted method")
        .doesNotContainIgnoringCase("enable-native-access");
  }

  @Test
  @DisplayName("AC-APP-12: the jar exits with status 0 within a few seconds when stdin closes")
  void the_jar_exits_cleanly_when_stdin_closes() throws Exception {
    Process server = ServerProcess.start(jarArguments(), config(), dir.resolve("data"));
    ServerProcess.collectLines(server.getInputStream());
    List<String> stderr = ServerProcess.collectLines(server.getErrorStream());

    server.getOutputStream().close();

    assertThat(server.waitFor(15, TimeUnit.SECONDS)).as("exited within a few seconds").isTrue();
    assertThat(server.exitValue())
        .as("exit status; stderr:%n%s", String.join("\n", stderr))
        .isZero();
  }
}
