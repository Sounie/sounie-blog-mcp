package nz.sounie.blogmcp.search.domain;

/** Query text, site filter, published-date range and result limit. Valid only as a whole. */
public record SearchQuery(
    QueryText text, SiteFilter site, PublishedDateRange dates, ResultLimit limit) {

  public SearchFilters filters() {
    throw new UnsupportedOperationException("not implemented");
  }
}
