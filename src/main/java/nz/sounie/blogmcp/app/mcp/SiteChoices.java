package nz.sounie.blogmcp.app.mcp;

import java.util.List;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;

/** The configured site IDs, read once at startup: the {@code enum} of the {@code site} argument. */
public final class SiteChoices {

  private SiteChoices() {}

  /** The site IDs in configuration order. */
  public static SiteChoices of(List<String> siteIds) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /** The site IDs in configuration order. */
  public List<String> ids() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /**
   * The filter for one configured site.
   *
   * @throws InvalidToolArgument for an unknown site, listing the known ones
   */
  public SiteFilter filterFor(String site) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }
}
