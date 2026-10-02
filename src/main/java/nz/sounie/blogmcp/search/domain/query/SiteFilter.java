package nz.sounie.blogmcp.search.domain.query;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.post.SiteId;

/** Which sites a search covers. */
sealed interface SiteFilter {

  boolean includes(SiteId siteId);

  /** Every site. */
  record AnySite() implements SiteFilter {
    @Override
    public boolean includes(SiteId siteId) {
      return true;
    }
  }

  /** One site only. */
  record OnlySite(SiteId siteId) implements SiteFilter {
    public OnlySite {
      Objects.requireNonNull(siteId, "siteId");
    }

    @Override
    public boolean includes(SiteId candidate) {
      return siteId.equals(candidate);
    }
  }
}
