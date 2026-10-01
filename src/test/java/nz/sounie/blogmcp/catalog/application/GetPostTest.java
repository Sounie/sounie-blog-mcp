package nz.sounie.blogmcp.catalog.application;

import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import nz.sounie.blogmcp.catalog.adapter.out.InMemoryPostRepository;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GetPostTest {

  private static final Instant PUBLISHED = Instant.parse("2026-09-20T01:20:47Z");
  private static final Instant UPDATED = Instant.parse("2026-09-21T08:00:00Z");
  private static final String URL = "https://blog2.sounie.nz/2026/09/20/hello/";

  private final InMemoryPostRepository posts = new InMemoryPostRepository();
  private final GetPost getPost = new GetPost(posts);

  @BeforeEach
  void givenStoredPost() {
    posts.save(
        aSnapshot()
            .sourcePostId("123")
            .url(URL)
            .title("Hello")
            .body("Hello, world.")
            .completeness(BodyCompleteness.FULL)
            .tags("DDD", "Java")
            .publishedAt(PUBLISHED)
            .updatedAt(UPDATED)
            .buildStoredPost());
  }

  @Test
  @DisplayName("AC-CAT-29: get a post by ID")
  void returns_the_post_by_id() {
    assertThat(getPost.byId("sounie-wp:123"))
        .contains(
            new PostView(
                "sounie-wp:123",
                "sounie-wp",
                "Hello",
                URL,
                List.of("DDD", "Java"),
                PUBLISHED,
                UPDATED,
                "Hello, world.",
                BodyCompleteness.FULL));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://blog2.sounie.nz/2026/09/20/hello/",
        "http://BLOG2.sounie.nz/2026/09/20/hello#comments"
      })
  @DisplayName("AC-CAT-30: get a post by URL, with normalisation")
  void returns_the_post_by_normalised_url(String url) {
    assertThat(getPost.byUrl(url))
        .hasValueSatisfying(view -> assertThat(view.postId()).isEqualTo("sounie-wp:123"));
  }

  @Test
  @DisplayName("AC-CAT-31: an unknown post ID returns empty")
  void unknown_post_id_returns_empty() {
    assertThat(getPost.byId("sounie-wp:999")).isEmpty();
  }

  @Test
  @DisplayName("AC-CAT-31: an unknown URL returns empty")
  void unknown_url_returns_empty() {
    assertThat(getPost.byUrl("https://blog2.sounie.nz/nope/")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-id"})
  @DisplayName("AC-CAT-31: text that is not a post ID is an invalid reference")
  void text_that_is_not_a_post_id_is_rejected(String text) {
    assertThatThrownBy(() -> getPost.byId(text)).isInstanceOf(InvalidPostReference.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not a url"})
  @DisplayName("AC-CAT-31: text that is not an absolute http(s) URL is an invalid reference")
  void text_that_is_not_a_url_is_rejected(String text) {
    assertThatThrownBy(() -> getPost.byUrl(text)).isInstanceOf(InvalidPostReference.class);
  }
}
