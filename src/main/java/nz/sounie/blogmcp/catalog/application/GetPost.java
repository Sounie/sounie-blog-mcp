package nz.sounie.blogmcp.catalog.application;

import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.PostRepository;

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
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * Normalises the URL before looking it up: lower-case scheme and host, {@code http} treated as
   * {@code https}, no fragment, no default port, trailing slash ignored, query kept.
   *
   * @throws InvalidPostReference if the text is not an absolute http(s) URL
   */
  public Optional<PostView> byUrl(String url) {
    throw new UnsupportedOperationException("not implemented");
  }
}
