package nz.sounie.blogmcp.search.domain.query;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.text.QueryText;

/** Query text, site filter, published-date range and result limit. Valid only as a whole. */
public record SearchQuery(
    QueryText text, SiteFilter site, PublishedDateRange dates, ResultLimit limit) {

  public SearchQuery {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(site, "site");
    Objects.requireNonNull(dates, "dates");
    Objects.requireNonNull(limit, "limit");
  }

  public SearchFilters filters() {
    return SearchFilters.of(site, dates);
  }
}
