package nz.sounie.blogmcp.app.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The stdio MCP server {@code blog-mcp}. Built here, so the composition root never imports the SDK.
 */
public final class BlogMcpServer implements AutoCloseable {

  static final String NAME = "blog-mcp";

  private final McpSyncServer server;

  private BlogMcpServer(McpSyncServer server) {
    this.server = server;
  }

  /**
   * Builds and starts the server on the given streams: server info {@code blog-mcp} and the
   * version, the instructions, the tools capability, both tools, and the SDK's input validation
   * switched off.
   */
  public static BlogMcpServer start(
      InputStream in, OutputStream out, String version, BlogMcpTools tools) {
    McpJsonMapper mapper = McpJsonDefaults.getMapper();
    return new BlogMcpServer(
        McpServer.sync(new StdioServerTransportProvider(mapper, in, out))
            .serverInfo(NAME, version)
            .instructions(ToolDefinitions.instructions())
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .tools(tools.searchPosts(), tools.getPost())
            .validateToolInputs(false)
            .build());
  }

  /** Closes the server gracefully. */
  @Override
  public void close() {
    server.closeGracefully();
  }
}
