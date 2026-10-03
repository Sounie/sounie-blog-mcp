package nz.sounie.blogmcp.app;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.adapter.out.JsonFileSiteDirectory;

/**
 * Where the configuration and all state live.
 *
 * @param configFile {@code BLOG_MCP_CONFIG}, or {@code ~/.config/blog-mcp/sites.json}
 * @param dataDirectory {@code BLOG_MCP_DATA} if set and not blank, or {@code
 *     ~/.local/share/blog-mcp}
 */
public record AppPaths(Path configFile, Path dataDirectory) {

  private static final String DATA_VARIABLE = "BLOG_MCP_DATA";

  public static AppPaths resolve(Map<String, String> environment, Path userHome) {
    return new AppPaths(
        JsonFileSiteDirectory.resolvePath(environment, userHome),
        Optional.ofNullable(environment.get(DATA_VARIABLE))
            .filter(value -> !value.isBlank())
            .map(Path::of)
            .orElseGet(() -> userHome.resolve(".local").resolve("share").resolve("blog-mcp")));
  }
}
