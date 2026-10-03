package nz.sounie.blogmcp.app;

import java.nio.file.Path;
import java.util.Map;

/**
 * Where the configuration and all state live.
 *
 * @param configFile {@code BLOG_MCP_CONFIG}, or {@code ~/.config/blog-mcp/sites.json}
 * @param dataDirectory {@code BLOG_MCP_DATA} if set and not blank, or {@code
 *     ~/.local/share/blog-mcp}
 */
public record AppPaths(Path configFile, Path dataDirectory) {

  public static AppPaths resolve(Map<String, String> environment, Path userHome) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }
}
