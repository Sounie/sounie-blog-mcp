package nz.sounie.blogmcp.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import nz.sounie.blogmcp.app.mcp.BlogMcpServer;
import nz.sounie.blogmcp.app.mcp.BlogMcpTools;
import nz.sounie.blogmcp.app.mcp.SiteChoices;
import nz.sounie.blogmcp.catalog.adapter.out.JsonFileSiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.InvalidSitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationMissing;

/** Starts the stdio MCP server {@code blog-mcp} (app.md 3.4). */
public final class Main {

  private Main() {}

  public static void main(String[] args) throws InterruptedException {
    PrintStream protocol = StdoutGuard.install();
    try {
      serveUntilEndOfInput(protocol);
    } catch (StartupFailure e) {
      System.err.println("blog-mcp cannot start: " + e.getMessage());
      System.exit(1);
    }
    System.exit(0);
  }

  private static void serveUntilEndOfInput(PrintStream protocol) throws InterruptedException {
    AppPaths paths = AppPaths.resolve(System.getenv(), Path.of(System.getProperty("user.home")));
    JsonFileSiteDirectory sites = new JsonFileSiteDirectory(paths.configFile());
    SitesConfiguration configuration = load(sites);
    createDataDirectory(paths.dataDirectory());

    Wiring wiring =
        Wiring.assemble(paths.dataDirectory(), sites, Adapters.production(), System.err);
    BlogMcpTools tools =
        new BlogMcpTools(
            wiring.searchPosts(), wiring.getPost(), siteChoices(configuration), System.err);
    EndOfInputWatch input = EndOfInputWatch.wrap(System.in);
    BlogMcpServer server = BlogMcpServer.start(input.stream(), protocol, version(), tools);
    try (ExecutorJobTimer timer = new ExecutorJobTimer()) {
      wiring.schedule(timer, configuration.syncInterval());
      input.awaitEnd();
    } finally {
      server.close();
    }
  }

  private static SitesConfiguration load(JsonFileSiteDirectory sites) {
    try {
      return sites.load();
    } catch (SitesConfigurationMissing | InvalidSitesConfiguration | IllegalStateException e) {
      // IllegalStateException: the file is not valid JSON.
      throw new StartupFailure(e.getMessage());
    }
  }

  private static void createDataDirectory(Path dataDirectory) {
    try {
      Files.createDirectories(dataDirectory);
    } catch (IOException e) {
      throw new StartupFailure("Cannot create the data directory " + dataDirectory + ": " + e);
    }
  }

  private static SiteChoices siteChoices(SitesConfiguration configuration) {
    return SiteChoices.of(configuration.sites().stream().map(site -> site.id().value()).toList());
  }

  /** {@code Implementation-Version} from the jar manifest; {@code dev} when run from classes. */
  private static String version() {
    return Optional.ofNullable(Main.class.getPackage().getImplementationVersion()).orElse("dev");
  }
}
