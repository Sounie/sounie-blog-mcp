package nz.sounie.blogmcp.search.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.application.SearchContext;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.query.SearchQueryBuilder;
import nz.sounie.blogmcp.search.domain.text.Words;
import nz.sounie.blogmcp.shared.event.CatalogPostPublished;
import nz.sounie.blogmcp.shared.event.CatalogPostRevised;
import nz.sounie.blogmcp.shared.event.CatalogPostWithdrawn;
import nz.sounie.blogmcp.shared.event.InProcessEventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogEventListenerTest {

  private static final Instant PUBLISHED = Instant.parse("2024-04-01T00:00:00Z");
  private static final String URL = "https://blog2.sounie.nz/records/";

  private final FakeEmbedder embedder = new FakeEmbedder();
  private final SearchContext search = new SearchContext(embedder);
  private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
  private final InProcessEventBus bus = new InProcessEventBus();

  CatalogEventListenerTest() {
    new CatalogEventListener(
            search.indexPost, new PrintStream(errors, true, StandardCharsets.UTF_8))
        .subscribeTo(bus);
  }

  private static CatalogPostPublished published(String postId, String siteId, String completeness) {
    return new CatalogPostPublished(
        postId,
        siteId,
        URL,
        "Records",
        Words.numbered(700),
        completeness,
        Set.of("Java"),
        PUBLISHED,
        PUBLISHED);
  }

  private String errorLog() {
    return errors.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("AC-SRCH-11: a published event on the bus indexes the post")
  void published_event_indexes_the_post() {
    bus.publish(published("sounie-wp:1", "sounie-wp", "FULL"));

    assertThat(search.index.find(PostId.parse("sounie-wp:1")))
        .hasValueSatisfying(p -> assertThat(p.chunks()).hasSize(3));
    assertThat(errorLog()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-16: a withdrawn event on the bus removes the post")
  void withdrawn_event_removes_the_post() {
    bus.publish(published("sounie-wp:1", "sounie-wp", "FULL"));

    bus.publish(new CatalogPostWithdrawn("sounie-wp:1", "sounie-wp", URL, "NO_LONGER_PUBLIC"));

    assertThat(search.index.ids()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-17: an embedder failure never breaks the publisher and keeps the old entry")
  void embedder_failure_is_logged_and_swallowed() {
    bus.publish(published("sounie-wp:1", "sounie-wp", "FULL"));
    IndexedPost before = search.index.find(PostId.parse("sounie-wp:1")).orElseThrow();
    embedder.failing();
    CatalogPostRevised revised =
        new CatalogPostRevised(
            "sounie-wp:1",
            "sounie-wp",
            URL,
            "Records",
            "A brand new body",
            "FULL",
            Set.of("Java"),
            PUBLISHED,
            PUBLISHED,
            Set.of("BODY"));

    assertThatCode(() -> bus.publish(revised)).doesNotThrowAnyException();

    assertThat(search.index.find(PostId.parse("sounie-wp:1"))).containsSame(before);
    assertThat(search.searchPosts.search(SearchQueryBuilder.aQuery("records").build()).matches())
        .hasSize(1);
    assertThat(errorLog()).contains("sounie-wp:1");
  }

  @ParameterizedTest(name = "{0} / {1} / {2}")
  @CsvSource({
    "sounie-wp-1, sounie-wp, FULL",
    "sounie-wp:1, elegant, FULL",
    "sounie-wp:1, sounie-wp, PARTIAL"
  })
  @DisplayName("AC-SRCH-18: a malformed payload is logged, not rethrown, and changes nothing")
  void malformed_payload_is_logged_and_swallowed(
      String postId, String siteId, String completeness) {
    assertThatCode(() -> bus.publish(published(postId, siteId, completeness)))
        .doesNotThrowAnyException();

    assertThat(search.index.ids()).isEmpty();
    assertThat(embedder.passageCallCount()).isZero();
    assertThat(errorLog()).contains(postId);
  }
}
