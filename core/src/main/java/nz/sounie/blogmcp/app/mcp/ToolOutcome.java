package nz.sounie.blogmcp.app.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;

/** What a tool handler produces. Each variant knows how to become an MCP result. */
public sealed interface ToolOutcome {

  CallToolResult toResult(McpJsonMapper mapper);

  /** The view as structured content, plus a text copy of the same JSON; {@code isError = false}. */
  record Answered(Object view) implements ToolOutcome {
    public Answered {
      Objects.requireNonNull(view, "view");
    }

    @Override
    public CallToolResult toResult(McpJsonMapper mapper) {
      Map<String, Object> json = mapper.convertValue(view, new TypeRef<Map<String, Object>>() {});
      return CallToolResult.builder()
          .structuredContent(json)
          .addTextContent(ToolOutcome.asText(mapper, json))
          .isError(false)
          .build();
    }
  }

  /** {@code {"found": false, "message": …}} in both forms; {@code isError = false}. */
  record NotFound(String message) implements ToolOutcome {
    public NotFound {
      Objects.requireNonNull(message, "message");
    }

    @Override
    public CallToolResult toResult(McpJsonMapper mapper) {
      return new Answered(Map.of("found", false, "message", message)).toResult(mapper);
    }
  }

  /** The message as text; {@code isError = true}. */
  record Failed(String message) implements ToolOutcome {
    public Failed {
      Objects.requireNonNull(message, "message");
    }

    @Override
    public CallToolResult toResult(McpJsonMapper mapper) {
      return CallToolResult.builder().addTextContent(message).isError(true).build();
    }
  }

  private static String asText(McpJsonMapper mapper, Map<String, Object> json) {
    try {
      return mapper.writeValueAsString(json);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write a tool result as JSON", e);
    }
  }
}
