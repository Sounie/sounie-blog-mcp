package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.catalog.domain.post.Body;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostImage;
import nz.sounie.blogmcp.catalog.domain.post.PostImages;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.catalog.domain.post.Title;

/**
 * A stored post as JSON ({@code "format": 2}), and the immutable snapshot the repository keeps in
 * memory. Restoring goes through the catalog value objects and {@link Post#restore}, so an invalid
 * value makes the file unreadable. A format 1 file has no {@code images}, and restores with none.
 */
record PostFile(
    Integer format,
    String postId,
    String canonicalUrl,
    String title,
    String body,
    String completeness,
    List<String> tags,
    List<ImageEntry> images,
    String publishedAt,
    String updatedAt) {

  /** A missing {@code images} field reads as none, for any readable format. */
  PostFile {
    tags = List.copyOf(tags);
    images = Optional.ofNullable(images).map(List::copyOf).orElse(List.of());
  }

  /** One post image as stored. */
  record ImageEntry(String url, String altText, String caption) {

    static ImageEntry of(PostImage image) {
      return new ImageEntry(image.url().toString(), image.altText(), image.caption());
    }

    PostImage toPostImage() {
      return new PostImage(URI.create(url), altText, caption);
    }
  }

  static PostFile of(Post post) {
    return new PostFile(
        StoredFormat.POST.written(),
        post.id().external(),
        post.url().value().toString(),
        post.title().value(),
        post.body().text(),
        post.completeness().name(),
        post.tags().stream().map(Tag::value).toList(),
        post.images().asList().stream().map(ImageEntry::of).toList(),
        post.publishedAt().toString(),
        post.updatedAt().toString());
  }

  /**
   * This file, if it restores a valid post.
   *
   * @throws RuntimeException naming the problem, if it does not
   */
  PostFile validated() {
    StoredFormat.POST.require(format);
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
        PostImages.of(images.stream().map(ImageEntry::toPostImage).toList()),
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
