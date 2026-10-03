package nz.sounie.blogmcp.app.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
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
      throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
    }
  }

  /** {@code {"found": false, "message": …}} in both forms; {@code isError = false}. */
  record NotFound(String message) implements ToolOutcome {
    public NotFound {
      Objects.requireNonNull(message, "message");
    }

    @Override
    public CallToolResult toResult(McpJsonMapper mapper) {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
    }
  }

  /** The message as text; {@code isError = true}. */
  record Failed(String message) implements ToolOutcome {
    public Failed {
      Objects.requireNonNull(message, "message");
    }

    @Override
    public CallToolResult toResult(McpJsonMapper mapper) {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
    }
  }
}
