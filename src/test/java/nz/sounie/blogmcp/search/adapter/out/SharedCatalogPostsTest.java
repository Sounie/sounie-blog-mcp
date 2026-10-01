package nz.sounie.blogmcp.search.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharedCatalogPostsTest {

  private static final Instant AT = Instant.parse("2024-04-01T00:00:00Z");

  private static CatalogPostState state(String postId, String siteId, String completeness) {
    return new CatalogPostState(
        postId,
        siteId,
        "https://" + siteId + ".example/" + postId,
        "Title " + postId,
        "Body",
        completeness,
        Set.of("Java"),
        AT,
        AT);
  }

  @Test
  @DisplayName("AC-SRCH-31, AC-SRCH-36: every state is translated in order, summary-only included")
  void translates_every_state_with_post_to_index_of() {
    CatalogPostState full = state("elegant:7", "elegant", "FULL");
    CatalogPostState summary = state("sounie-wp:2", "sounie-wp", "SUMMARY");

    var posts = new SharedCatalogPosts(new FakeCatalogPosts(full, summary)).currentPosts();

    assertThat(posts)
        .containsExactly(
            PostToIndex.of(
                full.postId(),
                full.siteId(),
                full.canonicalUrl(),
                full.title(),
                full.body(),
                full.completeness(),
                full.tags(),
                full.publishedAt(),
                full.updatedAt()),
            PostToIndex.of(
                summary.postId(),
                summary.siteId(),
                summary.canonicalUrl(),
                summary.title(),
                summary.body(),
                summary.completeness(),
                summary.tags(),
                summary.publishedAt(),
                summary.updatedAt()));
  }

  @Test
  void an_empty_catalog_gives_no_posts() {
    assertThat(new SharedCatalogPosts(new FakeCatalogPosts()).currentPosts()).isEmpty();
  }

  @Test
  @DisplayName("A malformed state is logged and skipped; the other posts are still translated")
  void skips_and_logs_a_malformed_state() {
    CatalogPostState malformed = state("sounie-wp:3", "elegant", "FULL");
    CatalogPostState valid = state("sounie-wp:4", "sounie-wp", "FULL");
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    var posts =
        new SharedCatalogPosts(
                new FakeCatalogPosts(malformed, valid),
                new PrintStream(errors, true, StandardCharsets.UTF_8))
            .currentPosts();
    assertThat(posts).extracting(post -> post.id().external()).containsExactly("sounie-wp:4");
    assertThat(errors.toString(StandardCharsets.UTF_8)).contains("sounie-wp:3");
  }
}
