package nz.sounie.blogmcp.app.mcp;

import java.util.List;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;

/** The configured site IDs, read once at startup: the {@code enum} of the {@code site} argument. */
public final class SiteChoices {

  private final List<String> ids;

  private SiteChoices(List<String> ids) {
    this.ids = List.copyOf(ids);
  }

  /** The site IDs in configuration order. */
  public static SiteChoices of(List<String> siteIds) {
    return new SiteChoices(siteIds);
  }

  /** The site IDs in configuration order. */
  public List<String> ids() {
    return ids;
  }

  /**
   * The filter for one configured site.
   *
   * @throws InvalidToolArgument for an unknown site, listing the known ones
   */
  public SiteFilter filterFor(String site) {
    if (!ids.contains(site)) {
      throw new InvalidToolArgument(
          "Unknown `site` '" + site + "'; known sites: " + String.join(", ", ids));
    }
    return new SiteFilter.OnlySite(new SiteId(site));
  }
}
