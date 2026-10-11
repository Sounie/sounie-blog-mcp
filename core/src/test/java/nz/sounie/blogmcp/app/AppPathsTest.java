package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** app.md glossary "Data directory" and "Config file". */
class AppPathsTest {

  private static final Path HOME = Path.of("/home/owner");

  @Test
  void BLOG_MCP_DATA_sets_the_data_directory() {
    AppPaths paths = AppPaths.resolve(Map.of("BLOG_MCP_DATA", "/srv/blog-mcp"), HOME);

    assertThat(paths.dataDirectory()).isEqualTo(Path.of("/srv/blog-mcp"));
  }

  @Test
  void the_default_data_directory_is_under_the_user_home() {
    AppPaths paths = AppPaths.resolve(Map.of(), HOME);

    assertThat(paths.dataDirectory()).isEqualTo(Path.of("/home/owner/.local/share/blog-mcp"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void a_blank_BLOG_MCP_DATA_means_the_default(String blank) {
    AppPaths paths = AppPaths.resolve(Map.of("BLOG_MCP_DATA", blank), HOME);

    assertThat(paths.dataDirectory()).isEqualTo(Path.of("/home/owner/.local/share/blog-mcp"));
  }

  @Test
  void the_config_file_follows_the_existing_rule() {
    assertThat(AppPaths.resolve(Map.of("BLOG_MCP_CONFIG", "/etc/sites.json"), HOME).configFile())
        .isEqualTo(Path.of("/etc/sites.json"));
    assertThat(AppPaths.resolve(Map.of(), HOME).configFile())
        .isEqualTo(Path.of("/home/owner/.config/blog-mcp/sites.json"));
  }
}
