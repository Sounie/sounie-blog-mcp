package nz.sounie.blogmcp.search.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.image.PostImage;
import nz.sounie.blogmcp.search.domain.index.Completeness;
import nz.sounie.blogmcp.search.domain.index.IndexChange;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.post.MalformedCatalogPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.shared.event.CatalogPostPublished;
import nz.sounie.blogmcp.shared.event.CatalogPostRevised;
import nz.sounie.blogmcp.shared.event.CatalogPostWithdrawn;
import nz.sounie.blogmcp.shared.query.CatalogPostImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CatalogEventTranslationTest {

  private static final Instant PUBLISHED = Instant.parse("2024-04-01T00:00:00Z");
  private static final Instant UPDATED = Instant.parse("2024-04-02T00:00:00Z");
  private static final String URL = "https://blog2.sounie.nz/records/";

  private static final List<CatalogPostImage> IMAGES =
      List.of(
          new CatalogPostImage("https://i0.wp.com/a.jpg", "Swan", ""),
          new CatalogPostImage("https://blog2.sounie.nz/b.png", "", "Grapes"));

  private static final PostToIndex EXPECTED =
      new PostToIndex(
          new PostId(new SiteId("sounie-wp"), "1"),
          Completeness.FULL,
          new PostMetadata(
              new SiteId("sounie-wp"), URL, "Records", Set.of("Java"), PUBLISHED, UPDATED),
          "Records",
          "Records are classes.",
          List.of(
              PostImage.of("https://i0.wp.com/a.jpg", "Swan", ""),
              PostImage.of("https://blog2.sounie.nz/b.png", "", "Grapes")));

  private static CatalogPostPublished published(List<CatalogPostImage> images) {
    return new CatalogPostPublished(
        "sounie-wp:1",
        "sounie-wp",
        URL,
        "Records",
        "Records are classes.",
        "FULL",
        Set.of("Java"),
        images,
        PUBLISHED,
        UPDATED);
  }

  @Test
  @DisplayName(
      "AC-SRCH-11, AC-SRCH-66: CatalogPostPublished becomes an upsert, its images in order")
  void published_becomes_upsert() {
    assertThat(CatalogEventTranslation.toChange(published(IMAGES)))
        .isEqualTo(new IndexChange.Upsert(EXPECTED));
  }

  @Test
  @DisplayName(
      "AC-SRCH-13, AC-SRCH-66: CatalogPostRevised becomes an upsert with its images; the changed"
          + " set is ignored")
  void revised_becomes_upsert() {
    CatalogPostRevised event =
        new CatalogPostRevised(
            "sounie-wp:1",
            "sounie-wp",
            URL,
            "Records",
            "Records are classes.",
            "FULL",
            Set.of("Java"),
            IMAGES,
            PUBLISHED,
            UPDATED,
            Set.of("IMAGES"));

    assertThat(CatalogEventTranslation.toChange(event)).isEqualTo(new IndexChange.Upsert(EXPECTED));
  }

  @Test
  @DisplayName("AC-SRCH-16: CatalogPostWithdrawn becomes a remove")
  void withdrawn_becomes_remove() {
    CatalogPostWithdrawn event =
        new CatalogPostWithdrawn("sounie-wp:1", "sounie-wp", URL, "NO_LONGER_LISTED");

    assertThat(CatalogEventTranslation.toChange(event))
        .isEqualTo(new IndexChange.Remove(new PostId(new SiteId("sounie-wp"), "1")));
  }

  @Test
  @DisplayName("AC-SRCH-18: a malformed payload is rejected")
  void malformed_payload_is_rejected() {
    CatalogPostPublished event =
        new CatalogPostPublished(
            "sounie-wp:1",
            "elegant",
            URL,
            "Records",
            "Body",
            "FULL",
            Set.of(),
            List.of(),
            PUBLISHED,
            UPDATED);

    assertThatThrownBy(() -> CatalogEventTranslation.toChange(event))
        .isInstanceOf(MalformedCatalogPost.class);
  }

  static List<CatalogPostImage> malformedImages() {
    return List.of(
        new CatalogPostImage("http://x.example/a.jpg", "", ""),
        new CatalogPostImage(null, "", ""),
        new CatalogPostImage("https://x.example/a.jpg", null, ""),
        new CatalogPostImage("https://x.example/a.jpg", "", null));
  }

  @ParameterizedTest
  @MethodSource("malformedImages")
  @DisplayName("AC-SRCH-66: an image with a null field or a non-https URL is malformed")
  void malformed_image_is_rejected(CatalogPostImage image) {
    assertThatThrownBy(() -> CatalogEventTranslation.toChange(published(List.of(image))))
        .isInstanceOf(MalformedCatalogPost.class);
  }
}
