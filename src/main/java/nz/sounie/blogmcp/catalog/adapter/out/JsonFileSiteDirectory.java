package nz.sounie.blogmcp.catalog.adapter.out;

import java.nio.file.Path;
import java.util.Map;
import nz.sounie.blogmcp.catalog.domain.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.SitesConfiguration;

/**
 * Loads the sites configuration from a JSON file of the form {@code {"sites": [{"id": "...",
 * "platform": "WORDPRESS", "baseUrl": "https://..."}]}}.
 */
public final class JsonFileSiteDirectory implements SiteDirectory {

  private final Path path;

  public JsonFileSiteDirectory(Path path) {
    this.path = path;
  }

  /**
   * The configuration path: {@code BLOG_MCP_CONFIG} if set, otherwise {@code
   * <userHome>/.config/blog-mcp/sites.json}.
   */
  public static Path resolvePath(Map<String, String> environment, Path userHome) {
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * @throws nz.sounie.blogmcp.catalog.domain.SitesConfigurationMissing naming the path, if the file
   *     does not exist
   * @throws nz.sounie.blogmcp.catalog.domain.InvalidSitesConfiguration if any rule is broken
   */
  @Override
  public SitesConfiguration load() {
    throw new UnsupportedOperationException("not implemented");
  }
}
