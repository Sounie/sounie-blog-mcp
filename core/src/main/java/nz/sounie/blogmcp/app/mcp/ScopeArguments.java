package nz.sounie.blogmcp.app.mcp;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.query.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.query.ResultLimit;
import nz.sounie.blogmcp.search.domain.query.SearchScope;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;

/**
 * The {@code site}, {@code from}, {@code to} and {@code limit} arguments that {@code search_posts}
 * and {@code find_posts_like_image} share, and their mapping to a {@link SearchScope} (app.md 3.1
 * and 3.9).
 */
final class ScopeArguments {

  static final String SITE = "site";
  static final String FROM = "from";
  static final String TO = "to";
  static final String LIMIT = "limit";

  /** The names this reader takes; each tool adds its own. */
  static final Set<String> NAMES = Set.of(SITE, FROM, TO, LIMIT);

  private final Optional<String> site;
  private final Optional<LocalDate> from;
  private final Optional<LocalDate> to;
  private final Optional<Integer> limit;

  private ScopeArguments(ToolArguments arguments) {
    this.site = arguments.optionalString(SITE);
    this.from = arguments.optionalDate(FROM);
    this.to = arguments.optionalDate(TO);
    this.limit = arguments.optionalInteger(LIMIT);
  }

  /**
   * @throws InvalidToolArgument for a mistyped argument
   */
  static ScopeArguments read(ToolArguments arguments) {
    return new ScopeArguments(arguments);
  }

  /**
   * Any site and the default limit when absent; an open end for an absent date.
   *
   * @throws InvalidToolArgument for an unknown site
   * @throws nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery for {@code from} after {@code
   *     to}
   */
  SearchScope toScope(SiteChoices sites) {
    return new SearchScope(
        site.map(sites::filterFor).orElseGet(SiteFilter.AnySite::new),
        PublishedDateRange.between(from.orElse(LocalDate.MIN), to.orElse(LocalDate.MAX)),
        limit.map(ResultLimit::of).orElseGet(ResultLimit::defaultLimit));
  }
}
