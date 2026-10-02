package nz.sounie.blogmcp.search.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.CatalogEntry;
import nz.sounie.blogmcp.search.domain.PostId;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wiring only: which {@link CatalogEntry} variant each state becomes. The field rules themselves
 * are tested on {@code PostToIndex}, {@code PostId}, {@code SiteId} and {@code Completeness}.
 */
class SharedCatalogPostsTest {

  private static final Instant AT = Instant.parse("2024-04-01T00:00:00Z");

  private static CatalogPostState state(String postId, String siteId, String completeness) {
    return state(postId, siteId, "Title " + postId, completeness);
  }

  private static CatalogPostState state(
      String postId, String siteId, String title, String completeness) {
    return new CatalogPostState(
        postId,
        siteId,
        "https://" + siteId + ".example/" + postId,
        title,
        "Body",
        completeness,
        Set.of("Java"),
        AT,
        AT);
  }

  private static PostToIndex translated(CatalogPostState s) {
    return PostToIndex.of(
        s.postId(),
        s.siteId(),
        s.canonicalUrl(),
        s.title(),
        s.body(),
        s.completeness(),
        s.tags(),
        s.publishedAt(),
        s.updatedAt());
  }

  @Test
  @DisplayName("AC-SRCH-31, AC-SRCH-36: readable states are translated in order, summary included")
  void translates_every_readable_state() {
    CatalogPostState full = state("elegant:7", "elegant", "FULL");
    CatalogPostState summary = state("sounie-wp:2", "sounie-wp", "SUMMARY");

    var entries = new SharedCatalogPosts(new FakeCatalogPosts(full, summary)).currentPosts();

    assertThat(entries)
        .containsExactly(
            new CatalogEntry.Readable(translated(full)),
            new CatalogEntry.Readable(translated(summary)));
  }

  @Test
  void an_empty_catalog_gives_no_entries() {
    assertThat(new SharedCatalogPosts(new FakeCatalogPosts()).currentPosts()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-38: a malformed field with a readable post ID is Unreadable")
  void malformed_field_with_readable_id_is_unreadable() {
    CatalogPostState malformed = state("sounie-wp:3", "sounie-wp", "PARTIAL");
    CatalogPostState valid = state("sounie-wp:4", "sounie-wp", "FULL");

    var entries = new SharedCatalogPosts(new FakeCatalogPosts(malformed, valid)).currentPosts();

    assertThat(entries).hasSize(2);
    assertThat(entries.getFirst())
        .isInstanceOfSatisfying(
            CatalogEntry.Unreadable.class,
            entry -> {
              assertThat(entry.id()).isEqualTo(PostId.parse("sounie-wp:3"));
              assertThat(entry.reason()).isNotBlank();
            });
    assertThat(entries.get(1)).isEqualTo(new CatalogEntry.Readable(translated(valid)));
  }

  @Test
  @DisplayName("AC-SRCH-38: a state whose post ID cannot be read is Unidentified")
  void unreadable_post_id_is_unidentified() {
    CatalogPostState noId = state("sounie-wp-3", "sounie-wp", "FULL");

    var entries = new SharedCatalogPosts(new FakeCatalogPosts(noId)).currentPosts();

    assertThat(entries)
        .singleElement()
        .isInstanceOfSatisfying(
            CatalogEntry.Unidentified.class,
            entry -> assertThat(entry.reason()).contains("sounie-wp-3"));
  }

  @Test
  @DisplayName("AC-SRCH-38: a null field with a readable post ID is Unreadable, not an NPE")
  void null_field_is_unreadable() {
    CatalogPostState nullTitle = state("elegant:5", "elegant", null, "FULL");

    var entries = new SharedCatalogPosts(new FakeCatalogPosts(nullTitle)).currentPosts();

    assertThat(entries)
        .singleElement()
        .isInstanceOfSatisfying(
            CatalogEntry.Unreadable.class,
            entry -> assertThat(entry.id()).isEqualTo(PostId.parse("elegant:5")));
  }

  @Test
  @DisplayName("AC-SRCH-38: a null tag element makes the entry Unreadable without aborting")
  void null_tag_is_unreadable() {
    CatalogPostState nullTag =
        new CatalogPostState(
            "elegant:6",
            "elegant",
            "https://elegant.example/6",
            "Six",
            "Body",
            "FULL",
            new java.util.HashSet<>(java.util.Arrays.asList("Java", null)),
            AT,
            AT);
    CatalogPostState valid = state("elegant:7", "elegant", "FULL");

    var entries = new SharedCatalogPosts(new FakeCatalogPosts(nullTag, valid)).currentPosts();

    assertThat(entries).hasSize(2);
    assertThat(entries.getFirst())
        .isInstanceOfSatisfying(
            CatalogEntry.Unreadable.class,
            entry -> assertThat(entry.id()).isEqualTo(PostId.parse("elegant:6")));
    assertThat(entries.get(1)).isEqualTo(new CatalogEntry.Readable(translated(valid)));
  }
}
