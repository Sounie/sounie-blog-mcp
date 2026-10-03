package nz.sounie.blogmcp.app.mcp;

import nz.sounie.blogmcp.search.domain.index.PostMatch;

/**
 * The JSON shape of one {@code search_posts} result.
 *
 * @param published the {@code yyyy-MM-dd} publication date in the blog time zone
 * @param score the similarity rounded half-up to 3 decimals
 */
public record SearchResultView(
    String postId,
    String title,
    String url,
    String site,
    String published,
    double score,
    String snippet) {

  public static SearchResultView of(PostMatch match) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }
}
