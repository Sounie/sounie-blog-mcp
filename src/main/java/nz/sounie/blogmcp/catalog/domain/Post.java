package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Set;

/** One published blog post as the catalog knows it. Aggregate root. */
public final class Post {

  private final PostId id;
  private CanonicalUrl url;
  private Title title;
  private Body body;
  private BodyCompleteness completeness;
  private Set<Tag> tags;
  private Instant publishedAt;
  private Instant updatedAt;

  private Post(
      PostId id,
      CanonicalUrl url,
      Title title,
      Body body,
      BodyCompleteness completeness,
      Set<Tag> tags,
      Instant publishedAt,
      Instant updatedAt) {
    this.id = id;
    this.url = url;
    this.title = title;
    this.body = body;
    this.completeness = completeness;
    this.tags = tags;
    this.publishedAt = publishedAt;
    this.updatedAt = updatedAt;
  }

  /** A newly published post together with its event. */
  public record Published(Post post, PostPublished event) {}

  /**
   * Adds a post the catalog has not seen before.
   *
   * @throws PostIdentityMismatch if the snapshot belongs to a different site
   * @throws CanonicalUrlNotOnSite if the snapshot URL is not on the site host
   */
  public static Published publish(Site site, PostSnapshot snapshot) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Rebuilds a stored post, for repositories. Performs no business logic. */
  public static Post restore(
      PostId id,
      CanonicalUrl url,
      Title title,
      Body body,
      BodyCompleteness completeness,
      Set<Tag> tags,
      Instant publishedAt,
      Instant updatedAt) {
    return new Post(id, url, title, body, completeness, tags, publishedAt, updatedAt);
  }

  /**
   * Applies a newer snapshot.
   *
   * @throws PostIdentityMismatch if the snapshot belongs to a different post
   */
  public Revision revise(PostSnapshot snapshot) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Removes the post from the catalog. The caller then deletes it from the repository. */
  public PostWithdrawn withdraw(WithdrawalReason reason) {
    throw new UnsupportedOperationException("not implemented");
  }

  public PostId id() {
    return id;
  }

  public CanonicalUrl url() {
    return url;
  }

  public Title title() {
    return title;
  }

  public Body body() {
    return body;
  }

  public BodyCompleteness completeness() {
    return completeness;
  }

  public Set<Tag> tags() {
    return tags;
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
