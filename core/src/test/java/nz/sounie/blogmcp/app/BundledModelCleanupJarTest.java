package nz.sounie.blogmcp.app;

import static nz.sounie.blogmcp.app.mcp.McpResults.isError;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-SRCH-51: the in-process Gemma2 jar extracts its model into its own temporary directory and
 * removes it when the JVM exits. Runs only in {@code :providers:gemma2:jarTest}.
 */
@Tag("jar")
@Tag("model")
@EnabledIfSystemProperty(
    named = "blogmcp.expectedProvider",
    matches = "\\Qnz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx.Gemma2OnnxEmbedder\\E")
class BundledModelCleanupJarTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(120);
  private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(15);

  @TempDir Path dir;
  @TempDir Path serverTmp;

  @Test
  @DisplayName("AC-SRCH-51: the extracted model directory is removed when the jar's JVM exits")
  void the_extracted_model_is_removed_when_the_jar_exits() throws Exception {
    Path config =
        Files.writeString(
            dir.resolve("sites.json"),
            """
            {"sites": [{"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://localhost:1"}]}
            """);
    List<String> args = new ArrayList<>(ServerProcess.sandboxOptions(serverTmp));
    args.addAll(List.of("-jar", System.getProperty("blogmcp.distributionJar")));
    StdioClientTransport transport =
        new StdioClientTransport(
            ServerProcess.parameters(args, config, dir.resolve("data")),
            McpJsonDefaults.getMapper());
    McpSyncClient client =
        McpClient.sync(transport)
            .requestTimeout(TIMEOUT)
            .initializationTimeout(INITIALIZATION_TIMEOUT)
            .build();
    client.initialize();
    CallToolResult result =
        client.callTool(
            CallToolRequest.builder("search_posts")
                .arguments(Map.of("query", "sourdough bread"))
                .build());
    assertThat(isError(result)).as("search_posts error").isFalse();
    assertThat(modelDirectories()).as("model extracted under the server's tmpdir").hasSize(1);

    // Closes the server's stdin and waits for its JVM to exit.
    assertThat(client.closeGracefully()).as("server exited").isTrue();
    assertThat(modelDirectories()).as("model directories left after exit").isEmpty();
  }

  private List<Path> modelDirectories() throws IOException {
    try (Stream<Path> entries = Files.list(serverTmp)) {
      return entries.filter(p -> p.getFileName().toString().startsWith("blog-mcp-model-")).toList();
    }
  }
}
