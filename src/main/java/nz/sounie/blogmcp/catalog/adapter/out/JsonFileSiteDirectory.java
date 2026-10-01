package nz.sounie.blogmcp.catalog.adapter.out;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.catalog.domain.SiteDefinition;
import nz.sounie.blogmcp.catalog.domain.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.SitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationMissing;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the sites configuration from a JSON file of the form {@code {"sites": [{"id": "...",
 * "platform": "WORDPRESS", "baseUrl": "https://..."}]}}.
 */
public final class JsonFileSiteDirectory implements SiteDirectory {

  private static final String CONFIG_VARIABLE = "BLOG_MCP_CONFIG";

  private final Path path;

  public JsonFileSiteDirectory(Path path) {
    this.path = path;
  }

  /**
   * The configuration path: {@code BLOG_MCP_CONFIG} if set, otherwise {@code
   * <userHome>/.config/blog-mcp/sites.json}.
   */
  public static Path resolvePath(Map<String, String> environment, Path userHome) {
    String configured = environment.get(CONFIG_VARIABLE);
    return configured != null && !configured.isBlank()
        ? Path.of(configured)
        : userHome.resolve(".config").resolve("blog-mcp").resolve("sites.json");
  }

  /**
   * @throws nz.sounie.blogmcp.catalog.domain.SitesConfigurationMissing naming the path, if the file
   *     does not exist
   * @throws nz.sounie.blogmcp.catalog.domain.InvalidSitesConfiguration if any rule is broken
   */
  @Override
  public SitesConfiguration load() {
    if (!Files.isRegularFile(path)) {
      throw new SitesConfigurationMissing(path);
    }
    JsonNode root;
    try {
      root = JsonMapper.builder().build().readTree(path);
    } catch (JacksonException e) {
      throw new IllegalStateException("Sites configuration at " + path + " is not valid JSON", e);
    }
    List<SiteDefinition> definitions = new ArrayList<>();
    for (JsonNode site : root.path("sites").values()) {
      definitions.add(
          new SiteDefinition(text(site, "id"), text(site, "platform"), text(site, "baseUrl")));
    }
    return SitesConfiguration.of(definitions);
  }

  /** The string value of a field, or {@code null} so validation reports it. */
  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isString() ? value.stringValue() : null;
  }
}
