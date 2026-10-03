package nz.sounie.blogmcp.app.mcp;

import static nz.sounie.blogmcp.app.mcp.McpResults.MAPPER;
import static nz.sounie.blogmcp.app.mcp.McpResults.isError;
import static nz.sounie.blogmcp.app.mcp.McpResults.structured;
import static nz.sounie.blogmcp.app.mcp.McpResults.text;
import static nz.sounie.blogmcp.app.mcp.McpResults.textAsJson;
import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** app.md 3.1: each outcome variant knows how to become an MCP result. */
class ToolOutcomeTest {

  record Example(String name, List<Integer> numbers) {}

  @Test
  void an_answer_is_structured_content_with_a_text_copy_of_the_same_json() {
    CallToolResult result =
        new ToolOutcome.Answered(new Example("records", List.of(1, 2))).toResult(MAPPER);

    assertThat(isError(result)).isFalse();
    assertThat(structured(result)).isEqualTo(Map.of("name", "records", "numbers", List.of(1, 2)));
    assertThat(textAsJson(result)).isEqualTo(structured(result));
  }

  @Test
  void not_found_is_a_normal_answer_saying_so_in_both_forms() {
    CallToolResult result = new ToolOutcome.NotFound("No post sounie-wp:999").toResult(MAPPER);

    assertThat(isError(result)).isFalse();
    assertThat(structured(result))
        .isEqualTo(Map.of("found", false, "message", "No post sounie-wp:999"));
    assertThat(textAsJson(result)).isEqualTo(structured(result));
  }

  @Test
  void a_failure_is_a_tool_error_with_the_message_as_text() {
    CallToolResult result =
        new ToolOutcome.Failed("`limit` must be a whole number").toResult(MAPPER);

    assertThat(isError(result)).isTrue();
    assertThat(text(result)).isEqualTo("`limit` must be a whole number");
  }
}
