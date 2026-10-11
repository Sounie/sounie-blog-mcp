package nz.sounie.blogmcp.catalog.adapter.out;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.application.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.SiteDefinition;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationMissing;
import nz.sounie.blogmcp.catalog.domain.site.SyncIntervalSetting;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the sites configuration from a JSON file of the form {@code {"syncEveryHours": 24, "sites":
 * [{"id": "...", "platform": "WORDPRESS", "baseUrl": "https://..."}]}}, where {@code
 * syncEveryHours} is optional.
 */
public final class JsonFileSiteDirectory implements SiteDirectory {

  private static final String CONFIG_VARIABLE = "BLOG_MCP_CONFIG";
  private static final String SYNC_INTERVAL_FIELD = "syncEveryHours";

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
   * @throws nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationMissing naming the path, if the
   *     file does not exist
   * @throws nz.sounie.blogmcp.catalog.domain.site.InvalidSitesConfiguration if any rule is broken
   */
  @Override
  public SitesConfiguration load() {
    JsonNode root = readJson();
    return SitesConfiguration.of(definitionsIn(root), syncIntervalIn(root));
  }

  /**
   * The raw {@code syncEveryHours}: absent is {@code Omitted}, an integral number is {@code
   * WholeHours}, anything else is {@code Unparseable} so validation reports it.
   */
  private static SyncIntervalSetting syncIntervalIn(JsonNode root) {
    return Optional.ofNullable(root.get(SYNC_INTERVAL_FIELD))
        .map(JsonFileSiteDirectory::syncIntervalOf)
        .orElseGet(SyncIntervalSetting.Omitted::new);
  }

  private static SyncIntervalSetting syncIntervalOf(JsonNode value) {
    return value.isIntegralNumber() && value.canConvertToLong()
        ? new SyncIntervalSetting.WholeHours(value.longValue())
        : new SyncIntervalSetting.Unparseable(value.toString());
  }

  private JsonNode readJson() {
    if (!Files.isRegularFile(path)) {
      throw new SitesConfigurationMissing(path);
    }
    try {
      return JsonMapper.builder().build().readTree(path);
    } catch (JacksonException e) {
      throw new IllegalStateException("Sites configuration at " + path + " is not valid JSON", e);
    }
  }

  /**
   * Raw definitions in file order; a missing {@code sites} list is empty, so validation reports it.
   */
  private static List<SiteDefinition> definitionsIn(JsonNode root) {
    return root.path("sites").values().stream()
        .map(
            site ->
                new SiteDefinition(text(site, "id"), text(site, "platform"), text(site, "baseUrl")))
        .toList();
  }

  /** The string value of a field, or {@code null} so validation reports it. */
  private static String text(JsonNode node, String field) {
    return Optional.of(node.path(field))
        .filter(JsonNode::isString)
        .map(JsonNode::stringValue)
        .orElse(null);
  }
}
