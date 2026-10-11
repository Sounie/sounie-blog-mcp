package nz.sounie.blogmcp.app;

import static nz.sounie.blogmcp.app.ServerProcess.eventually;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.app.mcp.ToolDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The server as a real subprocess: the real {@code McpClient} where only the protocol matters, raw
 * JSON-RPC lines where every byte on stdout must be checked (app.md section 6).
 */
class McpServerProcessTest {

  private static final McpJsonMapper MAPPER = McpJsonDefaults.getMapper();
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @TempDir Path dir;

  private Path config(String json) throws IOException {
    return Files.writeString(dir.resolve("sites.json"), json);
  }

  private Path twoSitesConfig() throws IOException {
    return config(
        """
        {"sites": [
          {"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://localhost:1"},
          {"id": "elegant", "platform": "BLOGGER", "baseUrl": "https://localhost:2"}
        ]}
        """);
  }

  private static Map<String, Object> json(String text) throws IOException {
    return MAPPER.readValue(text, new TypeRef<Map<String, Object>>() {});
  }

  private static Map<String, Object> object(Object value) {
    return MAPPER.convertValue(value, new TypeRef<Map<String, Object>>() {});
  }

  @Test
  @DisplayName("AC-APP-1: the server advertises exactly the two read-only tools")
  void advertises_exactly_the_two_read_only_tools() throws IOException {
    StdioClientTransport transport =
        new StdioClientTransport(
            ServerProcess.parameters(
                ServerProcess.mainArguments(), twoSitesConfig(), dir.resolve("data")),
            MAPPER);

    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(TIMEOUT).initializationTimeout(TIMEOUT).build()) {
      InitializeResult init = client.initialize();
      Map<String, Tool> tools =
          client.listTools().tools().stream()
              .collect(Collectors.toMap(Tool::name, Function.identity()));

      assertThat(init.serverInfo().name()).isEqualTo("blog-mcp");
      assertThat(init.capabilities().tools()).as("tools capability").isNotNull();
      assertThat(init.instructions()).contains(ToolDefinitions.CONTENT_NOTICE);
      assertThat(tools).containsOnlyKeys("search_posts", "get_post");

      Tool search = tools.get("search_posts");
      assertThat(search.title()).isEqualTo("Search the owner's blog posts");
      assertThat(search.description())
          .contains("sounie-wp")
          .contains("elegant")
          .contains("get_post")
          .contains(ToolDefinitions.CONTENT_NOTICE);
      assertReadOnly(search);
      Map<String, Object> searchInput = json(SEARCH_POSTS_INPUT_SCHEMA);
      assertThat(search.inputSchema())
          .containsEntry("type", "object")
          .containsEntry("additionalProperties", false)
          .containsEntry("required", List.of("query"))
          .containsEntry("properties", searchInput.get("properties"));
      assertThat(object(search.outputSchema().get("properties"))).containsKey("results");

      Tool get = tools.get("get_post");
      assertThat(get.title()).isEqualTo("Get one of the owner's blog posts");
      assertThat(get.description()).contains(ToolDefinitions.CONTENT_NOTICE);
      assertReadOnly(get);
      Map<String, Object> getInput = json(GET_POST_INPUT_SCHEMA);
      assertThat(get.inputSchema())
          .containsEntry("type", "object")
          .containsEntry("additionalProperties", false)
          .containsEntry("required", List.of("post"))
          .containsEntry("properties", getInput.get("properties"));
      assertThat(object(get.outputSchema().get("properties")))
          .containsKeys("found", "post", "message");
    }
  }

  private static void assertReadOnly(Tool tool) {
    ToolAnnotations annotations = tool.annotations();
    assertThat(annotations).as("annotations of %s", tool.name()).isNotNull();
    assertThat(annotations.readOnlyHint()).isTrue();
    assertThat(annotations.destructiveHint()).isFalse();
    assertThat(annotations.idempotentHint()).isTrue();
    assertThat(annotations.openWorldHint()).isFalse();
    assertThat(annotations.title()).isEqualTo(tool.title());
  }

  @Test
  @DisplayName("AC-APP-7: nothing but protocol on stdout, even when the startup sync fails")
  void nothing_but_protocol_on_stdout() throws Exception {
    Process server =
        ServerProcess.start(ServerProcess.mainArguments(), twoSitesConfig(), dir.resolve("data"));
    List<String> stdout = ServerProcess.collectLines(server.getInputStream());
    List<String> stderr = ServerProcess.collectLines(server.getErrorStream());

    try (OutputStream stdin = server.getOutputStream()) {
      send(
          stdin,
          """
          {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
          "capabilities":{},"clientInfo":{"name":"ac-app-7","version":"1"}}}""");
      eventually(TIMEOUT, "initialize response", () -> responded(stdout, 1));
      send(stdin, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
      send(
          stdin,
          """
          {"jsonrpc":"2.0","id":2,"method":"tools/call",\
          "params":{"name":"get_post","arguments":{"post":"sounie-wp:1"}}}""");
      send(
          stdin,
          """
          {"jsonrpc":"2.0","id":3,"method":"tools/call",\
          "params":{"name":"search_posts","arguments":{"query":"   "}}}""");
      eventually(
          TIMEOUT, "tool call responses", () -> responded(stdout, 2) && responded(stdout, 3));
      eventually(
          TIMEOUT,
          "the failed startup sync of sounie-wp on stderr",
          () -> String.join("\n", stderr).contains("sounie-wp"));
    }

    assertThat(server.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("exited").isTrue();
    assertThat(stdout).isNotEmpty();
    for (String line : stdout) {
      JsonNode message = JSON.readTree(line);
      assertThat(message.path("jsonrpc").asString()).as("JSON-RPC line: %s", line).isEqualTo("2.0");
    }
    assertThat(response(stdout, 2).path("result").path("isError").asBoolean(false)).isFalse();
    assertThat(response(stdout, 3).path("result").path("isError").asBoolean(false)).isTrue();
  }

  private static void send(OutputStream stdin, String line) throws IOException {
    stdin.write((line + "\n").getBytes(StandardCharsets.UTF_8));
    stdin.flush();
  }

  private static boolean responded(List<String> stdout, int id) {
    return stdout.stream().anyMatch(line -> line.contains("\"id\":" + id));
  }

  private static JsonNode response(List<String> stdout, int id) {
    return stdout.stream()
        .map(JSON::readTree)
        .filter(message -> message.path("id").asInt(-1) == id)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no response with id " + id));
  }

  @Test
  @DisplayName("AC-APP-8: a missing config file stops startup, naming the path")
  void a_missing_config_file_stops_startup() throws Exception {
    Path missing = dir.resolve("nowhere/sites.json");

    assertStartupFails(missing, dir.resolve("data"), missing.toString());
  }

  @Test
  @DisplayName("AC-APP-8: an invalid config stops startup, listing every violation")
  void an_invalid_config_stops_startup_listing_every_violation() throws Exception {
    Path config =
        config(
            """
            {"syncEveryHours": 0, "sites": [
              {"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "http://blog2.sounie.nz"}
            ]}
            """);

    assertStartupFails(config, dir.resolve("data"), "syncEveryHours", "http://blog2.sounie.nz");
  }

  @Test
  @DisplayName("AC-APP-8: an unparseable interval stops startup too, naming the value")
  void an_unparseable_interval_stops_startup() throws Exception {
    Path config =
        config(
            """
            {"syncEveryHours": "24", "sites": [
              {"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://blog2.sounie.nz"}
            ]}
            """);

    assertStartupFails(config, dir.resolve("data"), "syncEveryHours", "24");
  }

  @Test
  @DisplayName("AC-APP-8: a data directory that cannot be created stops startup, naming it")
  void an_uncreatable_data_directory_stops_startup() throws Exception {
    Path aFile = Files.writeString(dir.resolve("a-file"), "not a directory");
    Path data = aFile.resolve("data");

    assertStartupFails(twoSitesConfig(), data, data.toString());
  }

  private static void assertStartupFails(Path config, Path data, String... named) throws Exception {
    Process server = ServerProcess.start(ServerProcess.mainArguments(), config, data);
    List<String> stdout = ServerProcess.collectLines(server.getInputStream());
    List<String> stderr = ServerProcess.collectLines(server.getErrorStream());
    server.getOutputStream().close();

    assertThat(server.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("exited").isTrue();
    eventually(Duration.ofSeconds(5), "stderr", () -> !stderr.isEmpty());
    String errors = String.join("\n", stderr);
    assertThat(server.exitValue()).as("exit status; stderr:%n%s", errors).isEqualTo(1);
    assertThat(stdout).as("stdout").isEmpty();
    for (String fragment : named) {
      assertThat(errors).contains(fragment);
    }
    assertThat(errors).as("one clear message, not a stack trace").doesNotContain("\tat ");
  }

  private static final String SEARCH_POSTS_INPUT_SCHEMA =
      """
      {"type": "object", "additionalProperties": false, "required": ["query"],
       "properties": {
         "query": {"type": "string", "minLength": 1, "maxLength": 1000,
                   "description": "What to look for, in natural language."},
         "site":  {"type": "string", "enum": ["sounie-wp", "elegant"],
                   "description": "Only this site. Omit for all sites."},
         "from":  {"type": "string", "format": "date",
                   "description": "Earliest publication date, inclusive, yyyy-MM-dd, New Zealand time."},
         "to":    {"type": "string", "format": "date",
                   "description": "Latest publication date, inclusive, yyyy-MM-dd, New Zealand time."},
         "limit": {"type": "integer", "minimum": 1, "maximum": 20, "default": 10,
                   "description": "Maximum number of posts (1-20)."}}}
      """;

  private static final String GET_POST_INPUT_SCHEMA =
      """
      {"type": "object", "additionalProperties": false, "required": ["post"],
       "properties": {"post": {"type": "string", "minLength": 1,
         "description": "A post ID such as sounie-wp:123, or the post's URL."}}}
      """;
}
