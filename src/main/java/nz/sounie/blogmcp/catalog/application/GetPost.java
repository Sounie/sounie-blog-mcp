package nz.sounie.blogmcp.catalog.application;

import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.post.InvalidPostReference;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.post.WebAddress;

/** Looks up one post by post ID or by URL. */
public final class GetPost {

  private final PostRepository posts;

  public GetPost(PostRepository posts) {
    this.posts = posts;
  }

  /**
   * @param postId external form {@code <siteId>:<sourcePostId>}
   * @throws InvalidPostReference if the text is not a post ID
   */
  public Optional<PostView> byId(String postId) {
    return posts.findById(parsePostId(postId)).map(PostView::of);
  }

  /**
   * Normalises the URL before looking it up: lower-case scheme and host, {@code http} treated as
   * {@code https}, no fragment, no default port, trailing slash ignored, query kept.
   *
   * @throws InvalidPostReference if the text is not an absolute http(s) URL
   */
  public Optional<PostView> byUrl(String url) {
    return posts.findByCanonicalUrl(WebAddress.parse(url).asHttps()).map(PostView::of);
  }

  /**
   * Looks up a post by a reference that is either a post ID or a URL ({@link
   * nz.sounie.blogmcp.catalog.domain.post.PostReference}).
   *
   * @throws InvalidPostReference if the text is neither
   */
  public Optional<PostView> byReference(String reference) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.7)");
  }

  private static PostId parsePostId(String text) {
    try {
      return PostId.parse(text);
    } catch (IllegalArgumentException e) {
      throw new InvalidPostReference("Not a post ID (<siteId>:<sourcePostId>): '" + text + "'");
    }
  }
}
