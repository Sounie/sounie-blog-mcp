package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.catalog.domain.post.Body;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.catalog.domain.post.Title;

/**
 * A stored post as JSON ({@code "format": 1}), and the immutable snapshot the repository keeps in
 * memory. Restoring goes through the catalog value objects and {@link Post#restore}, so an invalid
 * value makes the file unreadable.
 */
record PostFile(
    Integer format,
    String postId,
    String canonicalUrl,
    String title,
    String body,
    String completeness,
    List<String> tags,
    String publishedAt,
    String updatedAt) {

  PostFile {
    tags = List.copyOf(tags);
  }

  static PostFile of(Post post) {
    return new PostFile(
        StoredFormat.CURRENT,
        post.id().external(),
        post.url().value().toString(),
        post.title().value(),
        post.body().text(),
        post.completeness().name(),
        post.tags().stream().map(Tag::value).toList(),
        post.publishedAt().toString(),
        post.updatedAt().toString());
  }

  /**
   * This file, if it restores a valid post.
   *
   * @throws RuntimeException naming the problem, if it does not
   */
  PostFile validated() {
    StoredFormat.require(format);
    toPost();
    return this;
  }

  /** A fresh post, restored from this snapshot. */
  Post toPost() {
    return Post.restore(
        id(),
        url(),
        new Title(title),
        new Body(body),
        BodyCompleteness.valueOf(completeness),
        tags.stream().map(Tag::new).collect(Collectors.toCollection(LinkedHashSet::new)),
        Instant.parse(publishedAt),
        Instant.parse(updatedAt));
  }

  PostId id() {
    return PostId.parse(postId);
  }

  CanonicalUrl url() {
    return new CanonicalUrl(URI.create(canonicalUrl));
  }
}
