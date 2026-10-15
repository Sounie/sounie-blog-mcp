package nz.sounie.blogmcp.app.mcp;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.query.SearchQuery;
import nz.sounie.blogmcp.search.domain.query.SearchScope;

/** The arguments of a {@code search_posts} call, and their mapping to a {@link SearchQuery}. */
public final class SearchPostsArguments {

  static final String QUERY = "query";

  private static final Set<String> NAMES =
      Stream.concat(Stream.of(QUERY), ScopeArguments.NAMES.stream())
          .collect(Collectors.toUnmodifiableSet());

  private final String query;
  private final ScopeArguments scope;

  private SearchPostsArguments(ToolArguments arguments) {
    this.query = arguments.requiredString(QUERY);
    this.scope = ScopeArguments.read(arguments);
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
    QueryText text = new QueryText(query);
    SearchScope within = scope.toScope(sites);
    return new SearchQuery(text, within.site(), within.dates(), within.limit());
  }
}
