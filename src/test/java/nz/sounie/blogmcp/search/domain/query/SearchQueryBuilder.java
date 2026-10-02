package nz.sounie.blogmcp.search.domain.query;

import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.text.QueryText;

/** Test data builder for {@link SearchQuery}. Defaults: any site, any date, the default limit. */
public final class SearchQueryBuilder {

  private final String text;
  private SiteFilter site = new SiteFilter.AnySite();
  private PublishedDateRange dates;
  private ResultLimit limit;

  private SearchQueryBuilder(String text) {
    this.text = text;
  }

  public static SearchQueryBuilder aQuery(String text) {
    return new SearchQueryBuilder(text);
  }

  public SearchQueryBuilder onlySite(String siteId) {
    this.site = new SiteFilter.OnlySite(new SiteId(siteId));
    return this;
  }

  public SearchQueryBuilder dates(PublishedDateRange dates) {
    this.dates = dates;
    return this;
  }

  public SearchQueryBuilder limit(int limit) {
    this.limit = ResultLimit.of(limit);
    return this;
  }

  public SearchQuery build() {
    return new SearchQuery(
        new QueryText(text),
        site,
        dates != null ? dates : PublishedDateRange.unbounded(),
        limit != null ? limit : ResultLimit.defaultLimit());
  }
}
