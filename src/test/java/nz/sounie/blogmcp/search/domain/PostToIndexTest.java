package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostToIndexTest {

  private static final Instant PUBLISHED = Instant.parse("2024-04-01T00:00:00Z");
  private static final Instant UPDATED = Instant.parse("2024-04-02T00:00:00Z");

  private static PostToIndex of(String postId, String siteId, String completeness) {
    return PostToIndex.of(
        postId,
        siteId,
        "https://blog2.sounie.nz/1/",
        "Title",
        "Body text",
        completeness,
        Set.of("Java"),
        PUBLISHED,
        UPDATED);
  }

  @Test
  @DisplayName("AC-SRCH-18: a valid payload becomes a post to index")
  void translates_a_valid_payload() {
    PostToIndex post = of("sounie-wp:1", "sounie-wp", "FULL");

    SiteId site = new SiteId("sounie-wp");
    assertThat(post)
        .isEqualTo(
            new PostToIndex(
                new PostId(site, "1"),
                Completeness.FULL,
                new PostMetadata(
                    site,
                    "https://blog2.sounie.nz/1/",
                    "Title",
                    Set.of("Java"),
                    PUBLISHED,
                    UPDATED),
                "Title",
                "Body text"));
  }

  @Test
  @DisplayName("AC-SRCH-18: summary completeness is translated")
  void translates_summary_completeness() {
    assertThat(of("elegant:5", "elegant", "SUMMARY").completeness())
        .isEqualTo(Completeness.SUMMARY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"sounie-wp", "sounie-wp:", ":1", ""})
  @DisplayName("AC-SRCH-18: a post ID not of the form <siteId>:<sourcePostId> is malformed")
  void rejects_malformed_post_id(String postId) {
    assertThatThrownBy(() -> of(postId, "sounie-wp", "FULL"))
        .isInstanceOf(MalformedCatalogPost.class);
  }

  @Test
  @DisplayName("AC-SRCH-18: a site ID that differs from the post ID's site part is malformed")
  void rejects_site_mismatch() {
    assertThatThrownBy(() -> of("sounie-wp:1", "elegant", "FULL"))
        .isInstanceOf(MalformedCatalogPost.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PARTIAL", "full", ""})
  @DisplayName("AC-SRCH-18: completeness other than FULL or SUMMARY is malformed")
  void rejects_unknown_completeness(String completeness) {
    assertThatThrownBy(() -> of("sounie-wp:1", "sounie-wp", completeness))
        .isInstanceOf(MalformedCatalogPost.class);
  }

  @ParameterizedTest(name = "argument {0} is null")
  @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
  @DisplayName("AC-SRCH-38: a null field is malformed, not a NullPointerException")
  void rejects_a_null_argument(int nullArgument) {
    Object[] args = {
      "sounie-wp:1",
      "sounie-wp",
      "https://blog2.sounie.nz/1/",
      "Title",
      "Body text",
      "FULL",
      Set.of("Java"),
      PUBLISHED,
      UPDATED
    };
    args[nullArgument] = null;

    assertThatThrownBy(
            () ->
                PostToIndex.of(
                    (String) args[0],
                    (String) args[1],
                    (String) args[2],
                    (String) args[3],
                    (String) args[4],
                    (String) args[5],
                    castTags(args[6]),
                    (Instant) args[7],
                    (Instant) args[8]))
        .isInstanceOf(MalformedCatalogPost.class);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> castTags(Object tags) {
    return (Set<String>) tags;
  }

  @Test
  @DisplayName("AC-SRCH-38: a null element in the tags is malformed, not a NullPointerException")
  void rejects_a_null_tag() {
    Set<String> tags = new java.util.HashSet<>(java.util.Arrays.asList("Java", null));

    assertThatThrownBy(
            () ->
                PostToIndex.of(
                    "sounie-wp:1",
                    "sounie-wp",
                    "https://blog2.sounie.nz/1/",
                    "Title",
                    "Body text",
                    "FULL",
                    tags,
                    PUBLISHED,
                    UPDATED))
        .isInstanceOf(MalformedCatalogPost.class);
  }
}
