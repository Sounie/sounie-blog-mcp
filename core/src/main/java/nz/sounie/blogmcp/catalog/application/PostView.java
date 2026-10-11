package nz.sounie.blogmcp.catalog.application;

import java.time.Instant;
import java.util.List;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.Tag;

/** Read model of one post, as returned by {@link GetPost}. */
public record PostView(
    String postId,
    String siteId,
    String title,
    String canonicalUrl,
    List<String> tags,
    Instant publishedAt,
    Instant updatedAt,
    String body,
    BodyCompleteness completeness) {

  public PostView {
    tags = List.copyOf(tags);
  }

  static PostView of(Post post) {
    return new PostView(
        post.id().external(),
        post.id().siteId().value(),
        post.title().value(),
        post.url().value().toString(),
        post.tags().stream().map(Tag::value).toList(),
        post.publishedAt(),
        post.updatedAt(),
        post.body().text(),
        post.completeness());
  }
}
