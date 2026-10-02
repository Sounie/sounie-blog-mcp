package nz.sounie.blogmcp.search.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogEventTranslationTest {

  private static final Instant PUBLISHED = Instant.parse("2024-04-01T00:00:00Z");
  private static final Instant UPDATED = Instant.parse("2024-04-02T00:00:00Z");
  private static final String URL = "https://blog2.sounie.nz/records/";

  private static final PostToIndex EXPECTED =
      new PostToIndex(
          new PostId(new SiteId("sounie-wp"), "1"),
          Completeness.FULL,
          new PostMetadata(
              new SiteId("sounie-wp"), URL, "Records", Set.of("Java"), PUBLISHED, UPDATED),
          "Records",
          "Records are classes.");

  @Test
  @DisplayName("AC-SRCH-11: CatalogPostPublished becomes an upsert of the post to index")
  void published_becomes_upsert() {
    CatalogPostPublished event =
        new CatalogPostPublished(
            "sounie-wp:1",
            "sounie-wp",
            URL,
            "Records",
            "Records are classes.",
            "FULL",
            Set.of("Java"),
            PUBLISHED,
            UPDATED);

    assertThat(CatalogEventTranslation.toChange(event)).isEqualTo(new IndexChange.Upsert(EXPECTED));
  }

  @Test
  @DisplayName("AC-SRCH-13: CatalogPostRevised becomes an upsert; the changed set is ignored")
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
            PUBLISHED,
            UPDATED,
            Set.of("TAGS"));

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
            "sounie-wp:1", "elegant", URL, "Records", "Body", "FULL", Set.of(), PUBLISHED, UPDATED);

    assertThatThrownBy(() -> CatalogEventTranslation.toChange(event))
        .isInstanceOf(MalformedCatalogPost.class);
  }
}
