package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-APP-51 and 54: each distribution jar lists {@code find_posts_like_image} exactly when its
 * provider can embed images. Listing tools loads no model, so this is not tagged {@code model} and
 * also runs against the {@code gemma2-server} jar, whose {@code jarTest} skips {@link
 * ShadowJarTest}.
 */
@Tag("jar")
class JarToolListTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @TempDir Path dir;

  @Test
  @DisplayName(
      "AC-APP-51; AC-APP-54: the jar lists find_posts_like_image only if it can embed images")
  void the_jar_lists_the_image_tool_only_when_image_capable() throws IOException {
    Path config =
        Files.writeString(
            dir.resolve("sites.json"),
            """
            {"sites": [{"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://localhost:1"}]}
            """);
    String jar = System.getProperty("blogmcp.distributionJar");
    assertThat(jar).as("system property blogmcp.distributionJar, set by jarTest").isNotBlank();
    List<String> args = new ArrayList<>(ServerProcess.sandboxOptions());
    args.addAll(List.of("-jar", jar));
    StdioClientTransport transport =
        new StdioClientTransport(
            ServerProcess.parameters(ServerProcess.java(), args, config, dir.resolve("data")),
            McpJsonDefaults.getMapper());

    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(TIMEOUT).initializationTimeout(TIMEOUT).build()) {
      client.initialize();

      assertThat(client.listTools().tools())
          .extracting(Tool::name)
          .containsExactlyInAnyOrderElementsOf(
              ShadowJarTest.expectedTools(ShadowJarTest.imageCapableJar()));
    }
  }
}
