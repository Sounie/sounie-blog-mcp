package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.List;
import nz.sounie.blogmcp.catalog.application.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.SiteDefinition;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfiguration;

/** Fake {@link SiteDirectory}: validates fixed definitions on each load, or always fails. */
public final class FixedSiteDirectory implements SiteDirectory {

  private final List<SiteDefinition> definitions;
  private final RuntimeException failure;

  private FixedSiteDirectory(List<SiteDefinition> definitions, RuntimeException failure) {
    this.definitions = definitions;
    this.failure = failure;
  }

  public static FixedSiteDirectory of(SiteDefinition... definitions) {
    return new FixedSiteDirectory(List.of(definitions), null);
  }

  public static FixedSiteDirectory failingWith(RuntimeException failure) {
    return new FixedSiteDirectory(List.of(), failure);
  }

  @Override
  public SitesConfiguration load() {
    if (failure != null) {
      throw failure;
    }
    return SitesConfiguration.of(definitions);
  }
}
