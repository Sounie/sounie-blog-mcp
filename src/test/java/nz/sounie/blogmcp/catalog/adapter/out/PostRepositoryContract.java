package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder;
import nz.sounie.blogmcp.catalog.domain.site.TestSites;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@link PostRepository} port's contract, run against every implementation (the in-memory fake
 * and the file repository), so swapping one for the other is safe.
 */
abstract class PostRepositoryContract {

  protected PostRepository repository;

  /** A fresh, empty repository. */
  protected abstract PostRepository newRepository();

  @BeforeEach
  void createRepository() {
    repository = newRepository();
  }

  protected static final PostSnapshotBuilder POST_123 =
      aSnapshot().sourcePostId("123").url("https://blog2.sounie.nz/2026/09/20/hello/");

  /** Compares field by field: {@link Post} has identity equality. */
  protected static void assertSamePost(Optional<Post> actual, Post expected) {
    assertThat(actual).isPresent();
    assertThat(actual.orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void finds_nothing_when_empty() {
    assertThat(repository.findById(POST_123.postId())).isEmpty();
    assertThat(repository.findIdsBySite(TestSites.SOUNIE_WP_ID)).isEmpty();
    assertThat(repository.findSiteIds()).isEmpty();
  }

  @Test
  void saves_and_finds_a_post_by_id() {
    Post post = POST_123.buildStoredPost();

    repository.save(post);

    assertSamePost(repository.findById(post.id()), post);
  }

  @Test
  void finds_a_post_by_its_normalised_canonical_url() {
    Post post = POST_123.buildStoredPost();
    repository.save(post);

    Optional<Post> found =
        repository.findByCanonicalUrl(
            new CanonicalUrl(URI.create("https://BLOG2.sounie.nz:443/2026/09/20/hello#comments")));

    assertSamePost(found, post);
  }

  @Test
  void finds_nothing_for_an_unknown_canonical_url() {
    repository.save(POST_123.buildStoredPost());

    assertThat(
            repository.findByCanonicalUrl(
                new CanonicalUrl(URI.create("https://blog2.sounie.nz/2026/09/20/other/"))))
        .isEmpty();
  }

  @Test
  void finds_ids_and_sites_of_stored_posts_only() {
    Post wp1 = aSnapshot().sourcePostId("1").buildStoredPost();
    Post wp2 = aSnapshot().sourcePostId("2").buildStoredPost();
    Post elegant = aSnapshot().on(TestSites.ELEGANT).sourcePostId("9").buildStoredPost();
    repository.save(wp1);
    repository.save(wp2);
    repository.save(elegant);

    assertThat(repository.findIdsBySite(TestSites.SOUNIE_WP_ID))
        .containsExactlyInAnyOrder(wp1.id(), wp2.id());
    assertThat(repository.findSiteIds())
        .containsExactlyInAnyOrder(TestSites.SOUNIE_WP_ID, TestSites.ELEGANT_ID);
  }

  @Test
  void save_replaces_the_stored_post() {
    repository.save(POST_123.buildStoredPost());
    Post newer = aSnapshot().sourcePostId("123").title("Newer").buildStoredPost();

    repository.save(newer);

    assertSamePost(repository.findById(newer.id()), newer);
    assertThat(repository.findIdsBySite(TestSites.SOUNIE_WP_ID)).containsExactly(newer.id());
  }

  @Test
  void delete_removes_the_post_and_its_site_when_it_was_the_last() {
    Post post = POST_123.buildStoredPost();
    repository.save(post);

    repository.delete(post.id());

    assertThat(repository.findById(post.id())).isEmpty();
    assertThat(repository.findByCanonicalUrl(post.url())).isEmpty();
    assertThat(repository.findSiteIds()).isEmpty();
  }

  @Test
  void deleting_an_unknown_post_does_nothing() {
    repository.save(POST_123.buildStoredPost());

    repository.delete(aSnapshot().sourcePostId("999").postId());

    assertThat(repository.findIdsBySite(TestSites.SOUNIE_WP_ID)).hasSize(1);
  }

  @Test
  @DisplayName("AC-APP-40: every find returns a fresh post")
  void every_find_returns_a_fresh_instance() {
    Post post = POST_123.buildStoredPost();
    repository.save(post);

    Post first = repository.findById(post.id()).orElseThrow();
    Post second = repository.findById(post.id()).orElseThrow();

    assertThat(first).isNotSameAs(second).isNotSameAs(post);
    assertThat(repository.findByCanonicalUrl(post.url()).orElseThrow()).isNotSameAs(first);
  }

  @Test
  @DisplayName("AC-APP-40: revising a found post changes nothing stored until it is saved")
  void unsaved_revision_is_not_visible() {
    Post stored = POST_123.buildStoredPost();
    repository.save(stored);
    Post found = repository.findById(stored.id()).orElseThrow();

    found.revise(TestSites.SOUNIE_WP, revisedSnapshot().build());

    assertSamePost(repository.findById(stored.id()), stored);
    assertSamePost(repository.findByCanonicalUrl(stored.url()), stored);
  }

  @Test
  @DisplayName("AC-APP-40: after save, a new find returns the revised post")
  void saved_revision_is_visible() {
    Post stored = POST_123.buildStoredPost();
    repository.save(stored);
    Post found = repository.findById(stored.id()).orElseThrow();
    found.revise(TestSites.SOUNIE_WP, revisedSnapshot().build());

    repository.save(found);

    assertSamePost(repository.findById(stored.id()), revisedSnapshot().buildStoredPost());
  }

  /** POST_123, revised: a new title and a later update. */
  protected static PostSnapshotBuilder revisedSnapshot() {
    return aSnapshot()
        .sourcePostId("123")
        .url("https://blog2.sounie.nz/2026/09/20/hello/")
        .title("Revised title")
        .updatedAt(Instant.parse("2026-02-01T00:00:00Z"));
  }
}
