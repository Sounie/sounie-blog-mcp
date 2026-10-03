package nz.sounie.blogmcp.app.mcp;

import java.util.List;
import nz.sounie.blogmcp.catalog.application.PostView;

/**
 * The JSON shape of a post returned by {@code get_post}.
 *
 * @param published the {@code yyyy-MM-dd} publication date in the blog time zone
 * @param publishedAt ISO-8601 UTC instant
 * @param updatedAt ISO-8601 UTC instant
 */
record GetPostView(
    String postId,
    String title,
    String url,
    String site,
    String published,
    String publishedAt,
    String updatedAt,
    List<String> tags,
    String completeness,
    String body) {

  static GetPostView of(PostView post) {
    return new GetPostView(
        post.postId(),
        post.title(),
        post.canonicalUrl(),
        post.siteId(),
        SearchResultView.publishedDate(post.publishedAt()),
        post.publishedAt().toString(),
        post.updatedAt().toString(),
        post.tags(),
        post.completeness().name(),
        post.body());
  }
}
