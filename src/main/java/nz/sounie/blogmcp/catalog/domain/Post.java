package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
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
    this.id = Objects.requireNonNull(id, "id");
    this.url = Objects.requireNonNull(url, "url");
    this.title = Objects.requireNonNull(title, "title");
    this.body = Objects.requireNonNull(body, "body");
    this.completeness = Objects.requireNonNull(completeness, "completeness");
    this.tags = unmodifiableCopy(tags);
    this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
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
    if (!snapshot.id().siteId().equals(site.id())) {
      throw new PostIdentityMismatch(
          "Snapshot " + snapshot.id().external() + " does not belong to site " + site.id().value());
    }
    if (!snapshot.url().isOn(site)) {
      throw new CanonicalUrlNotOnSite(
          "Canonical URL " + snapshot.url().value() + " is not on the site host " + site.host());
    }
    Post post =
        new Post(
            snapshot.id(),
            snapshot.url(),
            snapshot.title(),
            snapshot.body(),
            snapshot.completeness(),
            snapshot.tags(),
            snapshot.publishedAt(),
            snapshot.updatedAt());
    return new Published(
        post,
        new PostPublished(
            post.id,
            post.url,
            post.title,
            post.body,
            post.completeness,
            post.tags,
            post.publishedAt,
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
    return new Post(id, url, title, body, completeness, tags, publishedAt, updatedAt);
  }

  /**
   * Applies a newer snapshot.
   *
   * @throws PostIdentityMismatch if the snapshot belongs to a different post
   */
  public Revision revise(PostSnapshot snapshot) {
    if (!snapshot.id().equals(id)) {
      throw new PostIdentityMismatch(
          "Snapshot " + snapshot.id().external() + " does not belong to post " + id.external());
    }
    if (snapshot.updatedAt().isBefore(updatedAt)) {
      return new Revision.Stale();
    }
    Set<RevisedAspect> changed = materialChangesIn(snapshot);
    if (!changed.isEmpty()) {
      adopt(snapshot);
      return new Revision.Changed(
          new PostRevised(
              id, url, title, body, completeness, tags, publishedAt, updatedAt, changed));
    }
    if (snapshot.updatedAt().isAfter(updatedAt)) {
      updatedAt = snapshot.updatedAt();
      return new Revision.Touched();
    }
    return new Revision.Unchanged();
  }

  /** Removes the post from the catalog. The caller then deletes it from the repository. */
  public PostWithdrawn withdraw(WithdrawalReason reason) {
    return new PostWithdrawn(id, url, Objects.requireNonNull(reason, "reason"));
  }

  private Set<RevisedAspect> materialChangesIn(PostSnapshot snapshot) {
    Set<RevisedAspect> changed = EnumSet.noneOf(RevisedAspect.class);
    if (!snapshot.title().equals(title)) {
      changed.add(RevisedAspect.TITLE);
    }
    if (!snapshot.body().equals(body)) {
      changed.add(RevisedAspect.BODY);
    }
    if (snapshot.completeness() != completeness) {
      changed.add(RevisedAspect.COMPLETENESS);
    }
    if (!snapshot.tags().equals(tags)) {
      changed.add(RevisedAspect.TAGS);
    }
    if (!snapshot.url().equals(url)) {
      changed.add(RevisedAspect.URL);
    }
    if (!snapshot.publishedAt().equals(publishedAt)) {
      changed.add(RevisedAspect.PUBLISHED_AT);
    }
    return changed;
  }

  private void adopt(PostSnapshot snapshot) {
    url = snapshot.url();
    title = snapshot.title();
    body = snapshot.body();
    completeness = snapshot.completeness();
    tags = unmodifiableCopy(snapshot.tags());
    publishedAt = snapshot.publishedAt();
    updatedAt = snapshot.updatedAt();
  }

  private static Set<Tag> unmodifiableCopy(Set<Tag> tags) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
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
