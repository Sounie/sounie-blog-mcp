package nz.sounie.blogmcp.app.mcp;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.query.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.query.ResultLimit;
import nz.sounie.blogmcp.search.domain.query.SearchQuery;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;

/** The arguments of a {@code search_posts} call, and their mapping to a {@link SearchQuery}. */
public final class SearchPostsArguments {

  static final String QUERY = "query";
  static final String SITE = "site";
  static final String FROM = "from";
  static final String TO = "to";
  static final String LIMIT = "limit";

  private static final Set<String> NAMES = Set.of(QUERY, SITE, FROM, TO, LIMIT);

  private final String query;
  private final Optional<String> site;
  private final Optional<LocalDate> from;
  private final Optional<LocalDate> to;
  private final Optional<Integer> limit;

  private SearchPostsArguments(ToolArguments arguments) {
    this.query = arguments.requiredString(QUERY);
    this.site = arguments.optionalString(SITE);
    this.from = arguments.optionalDate(FROM);
    this.to = arguments.optionalDate(TO);
    this.limit = arguments.optionalInteger(LIMIT);
  }

  /**
   * Reads {@code query}, {@code site}, {@code from}, {@code to} and {@code limit}.
   *
   * @throws InvalidToolArgument for a missing, mistyped or unknown argument
   */
  public static SearchPostsArguments read(Map<String, Object> raw) {
    return new SearchPostsArguments(ToolArguments.of(raw, NAMES));
  }

  /**
   * @throws InvalidToolArgument for an unknown site
   * @throws nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery for a blank or long query, or
   *     {@code from} after {@code to}
   */
  public SearchQuery toQuery(SiteChoices sites) {
    return new SearchQuery(
        new QueryText(query),
        site.map(sites::filterFor).orElseGet(SiteFilter.AnySite::new),
        PublishedDateRange.between(from.orElse(LocalDate.MIN), to.orElse(LocalDate.MAX)),
        limit.map(ResultLimit::of).orElseGet(ResultLimit::defaultLimit));
  }
}
