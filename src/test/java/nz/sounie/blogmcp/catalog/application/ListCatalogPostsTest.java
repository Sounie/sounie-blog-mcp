package nz.sounie.blogmcp.catalog.application;

import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import nz.sounie.blogmcp.catalog.adapter.out.InMemoryPostRepository;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.TestSites;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListCatalogPostsTest {

  private static final Instant PUBLISHED = Instant.parse("2026-09-20T01:20:47Z");
  private static final Instant UPDATED = Instant.parse("2026-09-21T08:00:00Z");

  private final InMemoryPostRepository posts = new InMemoryPostRepository();
  private final ListCatalogPosts listCatalogPosts = new ListCatalogPosts(posts);

  @Test
  @DisplayName("AC-SRCH-31: current posts are listed with full state, sorted by post ID")
  void lists_every_post_sorted_by_post_id() {
    posts.save(aSnapshot().sourcePostId("2").buildStoredPost());
    posts.save(
        aSnapshot()
            .on(TestSites.ELEGANT)
            .sourcePostId("7")
            .url("https://blog.elegant-solutions.london/2026/09/seven.html")
            .title("Seven")
            .body("Only a teaser")
            .completeness(BodyCompleteness.SUMMARY)
            .tags("DDD", "Java")
            .publishedAt(PUBLISHED)
            .updatedAt(UPDATED)
            .buildStoredPost());
    posts.save(aSnapshot().sourcePostId("10").buildStoredPost());

    var states = listCatalogPosts.currentPosts();

    assertThat(states)
        .extracting(CatalogPostState::postId)
        .containsExactly("elegant:7", "sounie-wp:10", "sounie-wp:2");
    assertThat(states.getFirst())
        .isEqualTo(
            new CatalogPostState(
                "elegant:7",
                "elegant",
                "https://blog.elegant-solutions.london/2026/09/seven.html",
                "Seven",
                "Only a teaser",
                "SUMMARY",
                Set.of("DDD", "Java"),
                PUBLISHED,
                UPDATED));
    assertThat(states.get(1).completeness()).isEqualTo("FULL");
  }

  @Test
  @DisplayName("AC-SRCH-31: an empty catalog returns an empty list")
  void empty_catalog_lists_nothing() {
    assertThat(listCatalogPosts.currentPosts()).isEmpty();
  }
}
