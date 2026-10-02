package nz.sounie.blogmcp.search.domain.index;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.text.Words;

/** Test data builder for {@link PostToIndex}. Builds through constructors, not {@code of}. */
public final class PostToIndexBuilder {

  public static final Instant DEFAULT_PUBLISHED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private String siteId = "sounie-wp";
  private String sourcePostId = "1";
  private Completeness completeness = Completeness.FULL;
  private String url;
  private String title = "A title";
  private String body = "A body.";
  private Set<String> tags = new LinkedHashSet<>();
  private Instant publishedAt = DEFAULT_PUBLISHED_AT;
  private Instant updatedAt = DEFAULT_PUBLISHED_AT;

  private PostToIndexBuilder() {}

  public static PostToIndexBuilder aPost() {
    return new PostToIndexBuilder();
  }

  /** The post ID in external form, e.g. {@code sounie-wp:1}. */
  public PostToIndexBuilder id(String external) {
    int colon = external.indexOf(':');
    this.siteId = external.substring(0, colon);
    this.sourcePostId = external.substring(colon + 1);
    return this;
  }

  public PostToIndexBuilder completeness(Completeness completeness) {
    this.completeness = completeness;
    return this;
  }

  public PostToIndexBuilder summary() {
    return completeness(Completeness.SUMMARY);
  }

  public PostToIndexBuilder url(String url) {
    this.url = url;
    return this;
  }

  public PostToIndexBuilder title(String title) {
    this.title = title;
    return this;
  }

  public PostToIndexBuilder body(String body) {
    this.body = body;
    return this;
  }

  /** A body of {@code w1..w<n>}. */
  public PostToIndexBuilder words(int n) {
    return body(Words.numbered(n));
  }

  public PostToIndexBuilder tags(String... tags) {
    this.tags = new LinkedHashSet<>(List.of(tags));
    return this;
  }

  public PostToIndexBuilder publishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
    return this;
  }

  public PostToIndexBuilder updatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public PostId postId() {
    return new PostId(new SiteId(siteId), sourcePostId);
  }

  public PostMetadata metadata() {
    String link = url != null ? url : "https://" + siteId + ".example/" + sourcePostId + "/";
    return new PostMetadata(
        new SiteId(siteId), link, title, Set.copyOf(tags), publishedAt, updatedAt);
  }

  public PostToIndex build() {
    return new PostToIndex(postId(), completeness, metadata(), title, body);
  }
}
