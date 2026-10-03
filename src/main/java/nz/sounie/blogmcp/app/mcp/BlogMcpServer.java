package nz.sounie.blogmcp.app.mcp;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * The stdio MCP server {@code blog-mcp}. Built here, so the composition root never imports the SDK.
 */
public final class BlogMcpServer implements AutoCloseable {

  private BlogMcpServer() {}

  /**
   * Builds and starts the server on the given streams: server info {@code blog-mcp} and the
   * version, the instructions, the tools capability, both tools, and the SDK's input validation
   * switched off.
   */
  public static BlogMcpServer start(
      InputStream in, OutputStream out, String version, BlogMcpTools tools) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** Closes the server gracefully. */
  @Override
  public void close() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }
}
