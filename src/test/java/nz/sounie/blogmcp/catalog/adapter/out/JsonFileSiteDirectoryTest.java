package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.BASE_URL_NOT_ABSOLUTE_HTTPS;
import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.DUPLICATE_SITE_ID;
import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.UNSUPPORTED_PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import nz.sounie.blogmcp.catalog.domain.site.InvalidSitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationMissing;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation;
import nz.sounie.blogmcp.catalog.domain.site.TestSites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileSiteDirectoryTest {

  @TempDir Path dir;

  private Path write(String json) throws IOException {
    return Files.writeString(dir.resolve("sites.json"), json);
  }

  @Test
  void loads_the_configured_sites() throws IOException {
    Path file =
        write(
            """
            {"sites": [
              {"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://blog2.sounie.nz"},
              {"id": "elegant", "platform": "BLOGGER",
               "baseUrl": "https://blog.elegant-solutions.london"}
            ]}
            """);

    SitesConfiguration configuration = new JsonFileSiteDirectory(file).load();

    assertThat(configuration.sites()).containsExactly(TestSites.SOUNIE_WP, TestSites.ELEGANT);
  }

  @Test
  @DisplayName("AC-CAT-25: every violation in the file is reported together")
  void reports_every_violation_in_the_file() throws IOException {
    Path file =
        write(
            """
            {"sites": [
              {"id": "blog", "platform": "ghost", "baseUrl": "https://ghost.example"},
              {"id": "blog", "platform": "WORDPRESS", "baseUrl": "http://blog2.sounie.nz"}
            ]}
            """);

    InvalidSitesConfiguration failure =
        catchThrowableOfType(
            InvalidSitesConfiguration.class, () -> new JsonFileSiteDirectory(file).load());

    assertThat(failure).isNotNull();
    assertThat(failure.violations())
        .extracting(SitesConfigurationViolation::kind)
        .containsExactlyInAnyOrder(
            DUPLICATE_SITE_ID, UNSUPPORTED_PLATFORM, BASE_URL_NOT_ABSOLUTE_HTTPS);
  }

  @Test
  void loads_platform_names_written_in_lower_or_mixed_case() throws IOException {
    Path file =
        write(
            """
            {"sites": [
              {"id": "sounie-wp", "platform": "wordpress", "baseUrl": "https://blog2.sounie.nz"},
              {"id": "elegant", "platform": "Blogger",
               "baseUrl": "https://blog.elegant-solutions.london"}
            ]}
            """);

    SitesConfiguration configuration = new JsonFileSiteDirectory(file).load();

    assertThat(configuration.sites()).containsExactly(TestSites.SOUNIE_WP, TestSites.ELEGANT);
  }

  @Test
  @DisplayName("AC-CAT-25: a missing file is a startup error naming the path")
  void a_missing_file_is_reported_with_its_path() {
    Path missing = dir.resolve("nowhere/sites.json");

    assertThatThrownBy(() -> new JsonFileSiteDirectory(missing).load())
        .isInstanceOfSatisfying(
            SitesConfigurationMissing.class,
            failure -> {
              assertThat(failure.path()).isEqualTo(missing);
              assertThat(failure.getMessage()).contains(missing.toString());
            });
  }

  @Test
  @DisplayName("AC-CAT-25: BLOG_MCP_CONFIG overrides the default path")
  void resolves_the_path_from_BLOG_MCP_CONFIG() {
    Path path =
        JsonFileSiteDirectory.resolvePath(
            Map.of("BLOG_MCP_CONFIG", "/etc/blog-mcp/sites.json"), Path.of("/home/owner"));

    assertThat(path).isEqualTo(Path.of("/etc/blog-mcp/sites.json"));
  }

  @Test
  @DisplayName("AC-CAT-25: the default path is ~/.config/blog-mcp/sites.json")
  void resolves_the_default_path_under_the_user_home() {
    Path path = JsonFileSiteDirectory.resolvePath(Map.of(), Path.of("/home/owner"));

    assertThat(path).isEqualTo(Path.of("/home/owner/.config/blog-mcp/sites.json"));
  }
}
