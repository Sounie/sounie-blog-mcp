package nz.sounie.blogmcp.catalog.domain;

import java.nio.file.Path;

/** No sites configuration file exists at the resolved path. A startup error. */
public final class SitesConfigurationMissing extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Path path;

  public SitesConfigurationMissing(Path path) {
    super("No sites configuration at " + path);
    this.path = path;
  }

  public Path path() {
    return path;
  }
}
