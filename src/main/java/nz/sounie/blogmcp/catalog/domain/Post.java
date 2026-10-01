package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** One published blog post as the catalog knows it. Aggregate root. */
public final class Post {

  private final PostId id;
  private PostContent content;
  private Instant updatedAt;

  private Post(PostId id, PostContent content, Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.content = Objects.requireNonNull(content, "content");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
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
    requireSnapshotOf(site, snapshot);
    Post post = new Post(snapshot.id(), snapshot.content(), snapshot.updatedAt());
    PostContent c = post.content;
    return new Published(
        post,
        new PostPublished(
            post.id,
            c.url(),
            c.title(),
            c.body(),
            c.completeness(),
            c.tags(),
            c.publishedAt(),
            post.updatedAt));
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
    return new Post(
        id, new PostContent(url, title, body, completeness, tags, publishedAt), updatedAt);
  }

  /**
   * Applies a snapshot from the post's own site.
   *
   * @throws PostIdentityMismatch if the site is not this post's site, or the snapshot belongs to a
   *     different post
   * @throws CanonicalUrlNotOnSite if the snapshot URL is not on the site host
   */
  public Revision revise(Site site, PostSnapshot snapshot) {
    requireSamePost(snapshot);
    requireSnapshotOf(site, snapshot);
    if (snapshot.updatedAt().isBefore(updatedAt)) {
      return new Revision.Stale();
    }
    Set<RevisedAspect> changed = content.changedAspects(snapshot.content());
    return changed.isEmpty() ? recordTimestamp(snapshot.updatedAt()) : adopt(snapshot, changed);
  }

  /** Removes the post from the catalog. The caller then deletes it from the repository. */
  public PostWithdrawn withdraw(WithdrawalReason reason) {
    return new PostWithdrawn(id, content.url(), Objects.requireNonNull(reason, "reason"));
  }

  /**
   * Invariants 1 and 2, shared by publish and revise: the snapshot is of a post on this site, at a
   * URL on the site host.
   */
  private static void requireSnapshotOf(Site site, PostSnapshot snapshot) {
    if (!snapshot.id().siteId().equals(site.id())) {
      throw new PostIdentityMismatch(
          "Snapshot " + snapshot.id().external() + " does not belong to site " + site.id().value());
    }
    if (!snapshot.url().isOn(site)) {
      throw new CanonicalUrlNotOnSite(
          "Canonical URL " + snapshot.url().value() + " is not on the site host " + site.host());
    }
  }

  private void requireSamePost(PostSnapshot snapshot) {
    if (!snapshot.id().equals(id)) {
      throw new PostIdentityMismatch(
          "Snapshot " + snapshot.id().external() + " does not belong to post " + id.external());
    }
  }

  /** No material change: a newer timestamp is recorded (Touched), an equal one is Unchanged. */
  private Revision recordTimestamp(Instant snapshotUpdatedAt) {
    if (!snapshotUpdatedAt.isAfter(updatedAt)) {
      return new Revision.Unchanged();
    }
    updatedAt = snapshotUpdatedAt;
    return new Revision.Touched();
  }

  private Revision adopt(PostSnapshot snapshot, Set<RevisedAspect> changed) {
    content = snapshot.content();
    updatedAt = snapshot.updatedAt();
    return new Revision.Changed(
        new PostRevised(
            id,
            content.url(),
            content.title(),
            content.body(),
            content.completeness(),
            content.tags(),
            content.publishedAt(),
            updatedAt,
            changed));
  }

  public PostId id() {
    return id;
  }

  public CanonicalUrl url() {
    return content.url();
  }

  public Title title() {
    return content.title();
  }

  public Body body() {
    return content.body();
  }

  public BodyCompleteness completeness() {
    return content.completeness();
  }

  public Set<Tag> tags() {
    return content.tags();
  }

  public Instant publishedAt() {
    return content.publishedAt();
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
