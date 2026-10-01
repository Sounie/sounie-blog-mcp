package nz.sounie.blogmcp.catalog.domain;

/** A domain event raised by {@link Post}. */
public sealed interface PostEvent permits PostPublished, PostRevised, PostWithdrawn {

  PostId postId();

  default SiteId siteId() {
    return postId().siteId();
  }
}
