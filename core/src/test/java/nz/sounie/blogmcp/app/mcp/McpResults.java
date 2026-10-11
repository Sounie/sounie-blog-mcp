package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;

/** Reading real SDK results in tests, through the SDK's own JSON mapper. */
public final class McpResults {

  public static final McpJsonMapper MAPPER = McpJsonDefaults.getMapper();

  private McpResults() {}

  public static CallToolRequest request(String tool, Map<String, Object> arguments) {
    return CallToolRequest.builder(tool).arguments(arguments).build();
  }

  /** The structured content as a JSON object. */
  public static Map<String, Object> structured(CallToolResult result) {
    assertThat(result.structuredContent()).as("structured content").isNotNull();
    return MAPPER.convertValue(result.structuredContent(), new TypeRef<Map<String, Object>>() {});
  }

  /** The text of the single text content item. */
  public static String text(CallToolResult result) {
    return assertThat(result.content())
        .as("content")
        .singleElement(InstanceOfAssertFactories.type(TextContent.class))
        .actual()
        .text();
  }

  /** The text content parsed as a JSON object. */
  public static Map<String, Object> textAsJson(CallToolResult result) {
    try {
      return MAPPER.readValue(text(result), new TypeRef<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code results} of a search answer. */
  public static List<Map<String, Object>> results(CallToolResult result) {
    return MAPPER.convertValue(
        structured(result).get("results"), new TypeRef<List<Map<String, Object>>>() {});
  }

  /** {@code post} of a found get_post answer. */
  public static Map<String, Object> post(CallToolResult result) {
    return MAPPER.convertValue(
        structured(result).get("post"), new TypeRef<Map<String, Object>>() {});
  }

  public static boolean isError(CallToolResult result) {
    return Boolean.TRUE.equals(result.isError());
  }
}
