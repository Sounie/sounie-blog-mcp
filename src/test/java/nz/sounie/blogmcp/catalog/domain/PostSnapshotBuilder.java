package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Test data builder for {@link PostSnapshot}s and the stored {@link Post}s they describe. */
public final class PostSnapshotBuilder {

  public static final Instant DEFAULT_PUBLISHED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private Site site = TestSites.SOUNIE_WP;
  private String sourcePostId = "123";
  private String url;
  private String title = "A title";
  private String body = "A body.";
  private BodyCompleteness completeness = BodyCompleteness.FULL;
  private Set<Tag> tags = new LinkedHashSet<>();
  private Instant publishedAt = DEFAULT_PUBLISHED_AT;
  private Instant updatedAt = DEFAULT_PUBLISHED_AT;

  private PostSnapshotBuilder() {}

  public static PostSnapshotBuilder aSnapshot() {
    return new PostSnapshotBuilder();
  }

  public PostSnapshotBuilder on(Site site) {
    this.site = site;
    return this;
  }

  public PostSnapshotBuilder sourcePostId(String sourcePostId) {
    this.sourcePostId = sourcePostId;
    return this;
  }

  public PostSnapshotBuilder url(String url) {
    this.url = url;
    return this;
  }

  public PostSnapshotBuilder title(String title) {
    this.title = title;
    return this;
  }

  public PostSnapshotBuilder body(String body) {
    this.body = body;
    return this;
  }

  public PostSnapshotBuilder completeness(BodyCompleteness completeness) {
    this.completeness = completeness;
    return this;
  }

  public PostSnapshotBuilder tags(String... tags) {
    this.tags = new LinkedHashSet<>();
    Arrays.stream(tags).map(Tag::new).forEach(this.tags::add);
    return this;
  }

  public PostSnapshotBuilder publishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
    return this;
  }

  public PostSnapshotBuilder updatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public PostId postId() {
    return new PostId(site.id(), new SourcePostId(sourcePostId));
  }

  public CanonicalUrl canonicalUrl() {
    String link = url != null ? url : site.baseUrl() + "/" + sourcePostId + "/";
    return new CanonicalUrl(URI.create(link));
  }

  public PostSnapshot build() {
    return new PostSnapshot(
        postId(),
        canonicalUrl(),
        new Title(title),
        new Body(body),
        completeness,
        new LinkedHashSet<>(tags),
        publishedAt,
        updatedAt);
  }

  /** A stored post with exactly this builder's state, rebuilt without any business logic. */
  public Post buildStoredPost() {
    return Post.restore(
        postId(),
        canonicalUrl(),
        new Title(title),
        new Body(body),
        completeness,
        new LinkedHashSet<>(tags),
        publishedAt,
        updatedAt);
  }
}
