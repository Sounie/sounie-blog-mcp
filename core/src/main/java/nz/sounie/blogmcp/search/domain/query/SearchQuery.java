package nz.sounie.blogmcp.search.domain.query;

import java.util.Objects;

/** Query text, site filter, published-date range and result limit. Valid only as a whole. */
public record SearchQuery(
    QueryText text, SiteFilter site, PublishedDateRange dates, ResultLimit limit) {

  public SearchQuery {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(site, "site");
    Objects.requireNonNull(dates, "dates");
    Objects.requireNonNull(limit, "limit");
  }

  /** The site filter, the date range and the limit: everything but the text. */
  public SearchScope scope() {
    return new SearchScope(site, dates, limit);
  }

  public SearchFilters filters() {
    return scope().filters();
  }
}
