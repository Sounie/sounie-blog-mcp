package nz.sounie.blogmcp.app.mcp;

import java.util.Map;
import nz.sounie.blogmcp.search.domain.query.SearchQuery;

/** The arguments of a {@code search_posts} call, and their mapping to a {@link SearchQuery}. */
public final class SearchPostsArguments {

  private SearchPostsArguments() {}

  /**
   * Reads {@code query}, {@code site}, {@code from}, {@code to} and {@code limit}.
   *
   * @throws InvalidToolArgument for a missing, mistyped or unknown argument
   */
  public static SearchPostsArguments read(Map<String, Object> raw) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /**
   * @throws InvalidToolArgument for an unknown site
   * @throws nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery for a blank or long query, or
   *     {@code from} after {@code to}
   */
  public SearchQuery toQuery(SiteChoices sites) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }
}
