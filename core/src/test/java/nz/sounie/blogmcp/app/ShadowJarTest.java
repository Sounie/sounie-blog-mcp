package nz.sounie.blogmcp.app;

import static nz.sounie.blogmcp.app.mcp.McpResults.isError;
import static nz.sounie.blogmcp.app.mcp.McpResults.results;
import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.page;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.image;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLClassLoader;
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
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-APP-12: a provider distribution runs as an MCP server under the real client and exits when
 * stdin closes. AC-APP-54: the image-capable jar finds posts like an image. Run by each provider
 * module's {@code jarTest} against that module's jar.
 */
@Tag("jar")
@Tag("model")
class ShadowJarTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(120);
  private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(15);
  private static final SiteDefinition UNREACHABLE =
      new SiteDefinition("sounie-wp", "WORDPRESS", "https://localhost:1");

  @TempDir Path dir;

  /** The Java 25 launcher running the tests, not whichever JVM runs Gradle. */
  private static String launcher() {
    return ServerProcess.java();
  }

  private static List<String> launcherArguments() {
    String jar = System.getProperty("blogmcp.distributionJar");
    assertThat(jar).as("system property blogmcp.distributionJar, set by jarTest").isNotBlank();
    List<String> args = new ArrayList<>(ServerProcess.sandboxOptions());
    args.addAll(List.of("-jar", jar));
    return args;
  }

  private Path config() throws IOException {
    return Files.writeString(
        dir.resolve("sites.json"),
        """
                        {"sites": [{"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://localhost:1"}]}
                        """);
  }

  private static final Site SITE =
      new Site(new SiteId("sounie-wp"), Platform.WORDPRESS, URI.create(UNREACHABLE.baseUrl()));

  /** The post image of AC-APP-54's post I. */
  private static final String SWANS_IMAGE_URL = "https://localhost:1/images/black-swans.png";

  private static final String FIND_POSTS_LIKE_IMAGE = "find_posts_like_image";

  /**
   * Only the in-process {@code gemma2} provider can embed images (ADR 0011). Taken from what the
   * module's {@code jarTest} expects, not from the provider, so a provider that lost its image
   * embedder fails here instead of skipping.
   */
  static boolean imageCapableJar() {
    String provider = System.getProperty("blogmcp.expectedProvider", "");
    assertThat(provider)
        .as("system property blogmcp.expectedProvider, set by jarTest")
        .isNotBlank();
    return provider.endsWith(".gemma2.onnx.Gemma2OnnxEmbedder");
  }

  /** {@code search_posts} and {@code get_post}, plus the image tool for an image-capable jar. */
  static List<String> expectedTools(boolean imageCapable) {
    List<String> tools = new ArrayList<>(List.of("search_posts", "get_post"));
    if (imageCapable) {
      tools.add(FIND_POSTS_LIKE_IMAGE);
    }
    return tools;
  }

  /** The sourdough post: AC-APP-12's fixture, and AC-APP-54's decoy. */
  private static SourceEntry sourdough() {
    return post(
        "1",
        "https://localhost:1/sourdough/",
        "Baking sourdough bread at home",
        "Sourdough bread needs a lively starter, a long cold fermentation and a very hot oven.");
  }

  /** AC-APP-54's post W: the wording describes the query image; it has no image. */
  private static SourceEntry swanInWords() {
    return post(
        "2",
        "https://localhost:1/black-swan/",
        "A black swan among the reeds",
        "A black swan swam slowly through the tall green reeds at the edge of the lake, its red"
            + " beak bright against the dark feathers and the still water.");
  }

  /** AC-APP-54's post I: unrelated wording, with one related but different swan photograph. */
  private static SourceEntry swansInAnImage() {
    return new SourceEntry.Available(
        aSnapshot()
            .on(SITE)
            .sourcePostId("3")
            .url("https://localhost:1/sunday-walk/")
            .title("Sunday walk")
            .body(
                "We took the long way round after lunch, stopped for coffee and talked about"
                    + " the week ahead.")
            .images(image(SWANS_IMAGE_URL))
            .publishedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build());
  }

  private static SourceEntry post(String id, String url, String title, String body) {
    return new SourceEntry.Available(
        aSnapshot()
            .on(SITE)
            .sourcePostId(id)
            .url(url)
            .title(title)
            .body(body)
            .publishedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build());
  }

  /**
   * The posts and their index entries, written with the one discovered provider. Post images come
   * from the given fake of our {@code ImageSource} port, so no HTTP is needed; an image-capable
   * provider then embeds them as in production.
   */
  private Path dataWith(InMemoryImageSource postImages, SourceEntry... posts) throws IOException {
    Path data = dir.resolve("data");
    FakeBlogSource source = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);
    source.willServe(SITE.id(), page(posts));
    try (URLClassLoader distributionLoader =
        ProviderDistributionClasspathAcceptanceTest.distributionClassLoader()) {
      EmbeddingProvider model = EmbeddingProviderDiscovery.discover(distributionLoader);
      Wiring.assemble(
              data,
              FixedSiteDirectory.of(UNREACHABLE),
              Adapters.withProvider(
                      Map.of(Platform.WORDPRESS, source, Platform.BLOGGER, source),
                      model,
                      Clock.systemUTC())
                  .withPostImages(postImages),
              new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
          .job()
          .run();
    }
    return data;
  }

  private McpSyncClient client(Path config, Path data, List<String> stderr) {
    StdioClientTransport transport =
        new StdioClientTransport(
            ServerProcess.parameters(launcher(), launcherArguments(), config, data),
            McpJsonDefaults.getMapper());
    transport.setStdErrorHandler(stderr::add);
    return McpClient.sync(transport)
        .requestTimeout(TIMEOUT)
        .initializationTimeout(INITIALIZATION_TIMEOUT)
        .build();
  }

  @Test
  @DisplayName(
      "AC-APP-54: the gemma2 jar finds swan posts from the black swan image, by their wording and"
          + " by a related image")
  void the_image_capable_jar_finds_swan_posts_like_the_black_swan_image() throws IOException {
    assumeTrue(
        imageCapableJar(), "a text-only jar lists no find_posts_like_image (checked by AC-APP-12)");
    InMemoryImageSource postImages =
        new InMemoryImageSource()
            .fetching(SWANS_IMAGE_URL, InMemoryImageSource.pixelsOfResource("/black-swans.png"));
    Path config = config();
    Path data = dataWith(postImages, sourdough(), swanInWords(), swansInAnImage());
    assertThat(postImages.fetchedUrls())
        .as("post I's image was indexed")
        .containsExactly(URI.create(SWANS_IMAGE_URL));
    Path query = dir.resolve("black-swan-in-reeds.png");
    try (InputStream in = ShadowJarTest.class.getResourceAsStream("/black-swan-in-reeds.png")) {
      Files.copy(in, query);
    }
    List<String> stderr = new CopyOnWriteArrayList<>();

    try (McpSyncClient client = client(config, data, stderr)) {
      client.initialize();
      assertThat(client.listTools().tools()).extracting(Tool::name).contains(FIND_POSTS_LIKE_IMAGE);

      CallToolResult result =
          client.callTool(
              CallToolRequest.builder(FIND_POSTS_LIKE_IMAGE)
                  .arguments(Map.of("image", query.toAbsolutePath().toString()))
                  .build());

      assertThat(isError(result)).as("stderr:%n%s", String.join("\n", stderr)).isFalse();
      List<Map<String, Object>> results = results(result);
      assertThat(results)
          .as("ranked results (sounie-wp:1 is the sourdough decoy)")
          .extracting(r -> r.get("postId"))
          .hasSize(3)
          .endsWith("sounie-wp:1");
      assertThat(results.subList(0, 2))
          .extracting(r -> r.get("postId"))
          .containsExactlyInAnyOrder("sounie-wp:2", "sounie-wp:3");
      Map<String, Object> byWording = byPostId(results, "sounie-wp:2");
      Map<String, Object> byImage = byPostId(results, "sounie-wp:3");
      assertThat(byImage).containsEntry("matchedImage", SWANS_IMAGE_URL);
      assertThat(byWording).doesNotContainKey("matchedImage");
    }
  }

  private static Map<String, Object> byPostId(List<Map<String, Object>> results, String postId) {
    return results.stream().filter(r -> postId.equals(r.get("postId"))).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("AC-APP-12: the jar answers a real client from persisted data, with a clean stderr")
  void the_jar_serves_the_fixture_post_to_a_real_client() throws IOException {
    Path config = config();
    Path data = dataWith(new InMemoryImageSource(), sourdough());
    List<String> stderr = new CopyOnWriteArrayList<>();

    try (McpSyncClient client = client(config, data, stderr)) {
      client.initialize();
      assertThat(client.listTools().tools())
          .as("AC-APP-51: the image tool only for an image-capable jar")
          .extracting(Tool::name)
          .containsExactlyInAnyOrderElementsOf(expectedTools(imageCapableJar()));

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
    Process server =
        ServerProcess.start(launcher(), launcherArguments(), config(), dir.resolve("data"));
    ServerProcess.collectLines(server.getInputStream());
    List<String> stderr = ServerProcess.collectLines(server.getErrorStream());

    server.getOutputStream().close();

    assertThat(server.waitFor(15, TimeUnit.SECONDS)).as("exited within a few seconds").isTrue();
    assertThat(server.exitValue())
        .as("exit status; stderr:%n%s", String.join("\n", stderr))
        .isZero();
  }
}
