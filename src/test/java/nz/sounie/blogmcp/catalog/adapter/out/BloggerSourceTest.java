package nz.sounie.blogmcp.catalog.adapter.out;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static nz.sounie.blogmcp.catalog.domain.TestSites.ELEGANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePage;
import nz.sounie.blogmcp.catalog.domain.SourceUnavailable;
import nz.sounie.blogmcp.catalog.domain.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BloggerSourceTest {

  private static final String FEED = "/feeds/posts/default";

  @RegisterExtension
  static final WireMockExtension bloggerFeed =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private BloggerSource source;

  @BeforeEach
  void setUp() {
    source =
        new BloggerSource(
            HttpClient.newHttpClient(),
            new JsoupHtmlToText(),
            site -> URI.create(bloggerFeed.baseUrl()));
  }

  private void stubFeed(String body) {
    bloggerFeed.stubFor(get(urlPathEqualTo(FEED)).willReturn(okJson(body)));
  }

  private SourcePage fetchFirstPage() {
    return source.fetch(ELEGANT, Optional.empty(), PageCursor.first());
  }

  private static PostSnapshot snapshotOf(SourceEntry entry) {
    assertThat(entry).isInstanceOf(SourceEntry.Available.class);
    return ((SourceEntry.Available) entry).snapshot();
  }

  @Test
  void returns_changes_newest_first() {
    assertThat(source.changeOrder()).isEqualTo(ChangeOrder.NEWEST_FIRST);
  }

  @Test
  void maps_a_recorded_summary_feed_entry_to_a_summary_snapshot() {
    stubFeed(Fixtures.read("blogger/feed-recorded.json"));

    SourcePage page = fetchFirstPage();

    assertThat(page.entries()).hasSize(2);
    PostSnapshot first = snapshotOf(page.entries().getFirst());
    assertThat(first.id().external()).isEqualTo("elegant:371460637286630063");
    assertThat(first.url().value())
        .isEqualTo(
            URI.create(
                "https://blog.elegant-solutions.london/2026/03/experiences-with-microservices.html"));
    assertThat(first.title().value())
        .isEqualTo("Fresh posts about experiences developing and maintaining microservices");
    assertThat(first.completeness()).isEqualTo(BodyCompleteness.SUMMARY);
    assertThat(first.body().text())
        .startsWith("In March 2026 I got caught up in the 10% workforce reduction")
        .doesNotContain("&nbsp;");
    assertThat(first.tags())
        .extracting(Tag::value)
        .containsExactlyInAnyOrder("Atlassian 1600", "Fresh blogs", "March 2026");
    assertThat(snapshotOf(page.entries().get(1)).id().external())
        .isEqualTo("elegant:3145279241138581814");
  }

  @Test
  @DisplayName("AC-CAT-7: Blogger timestamps with an offset are converted to UTC")
  void reads_timestamps_with_their_offset_as_utc_instants() {
    stubFeed(Fixtures.read("blogger/feed-recorded.json"));

    List<SourceEntry> entries = fetchFirstPage().entries();

    PostSnapshot first = snapshotOf(entries.getFirst());
    assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-03-23T20:53:00.001Z"));
    assertThat(first.updatedAt()).isEqualTo(Instant.parse("2026-03-23T20:53:30.835Z"));
    PostSnapshot second = snapshotOf(entries.get(1));
    assertThat(second.publishedAt()).isEqualTo(Instant.parse("2021-10-06T20:38:00.004Z"));
  }

  @Test
  @DisplayName("Q1: content, when present, gives a FULL body; otherwise the summary is used")
  void uses_content_as_a_full_body_and_falls_back_to_summary() {
    stubFeed(Fixtures.read("blogger/feed-with-content.json"));

    List<SourceEntry> entries = fetchFirstPage().entries();

    PostSnapshot withContent = snapshotOf(entries.getFirst());
    assertThat(withContent.completeness()).isEqualTo(BodyCompleteness.FULL);
    assertThat(withContent.body().text())
        .containsSubsequence(
            "In March 2026 I got caught up", "\n\n", "This is the <full> text of the post.")
        .doesNotContain("<p>");
    assertThat(snapshotOf(entries.get(1)).completeness()).isEqualTo(BodyCompleteness.SUMMARY);
  }

  @Test
  @DisplayName("AC-CAT-9: Blogger labels become tags")
  void labels_become_case_insensitively_unique_tags() {
    stubFeed(Fixtures.read("blogger/feed-labels.json"));

    PostSnapshot snapshot = snapshotOf(fetchFirstPage().entries().getFirst());

    assertThat(snapshot.tags()).extracting(Tag::value).containsExactly("DDD", "Java");
  }

  @Test
  @DisplayName("AC-CAT-3: an empty Blogger feed needs exactly one request")
  void empty_feed_needs_one_request_and_has_no_more_pages() {
    stubFeed(Fixtures.read("blogger/feed-empty.json"));

    List<SourceEntry> entries = Fixtures.fetchAll(source, ELEGANT, Optional.empty());

    assertThat(entries).isEmpty();
    bloggerFeed.verify(1, getRequestedFor(urlPathEqualTo(FEED)));
  }

  @Test
  void stops_when_a_page_has_fewer_than_150_entries() {
    stubFeed(Fixtures.read("blogger/feed-recorded.json"));

    assertThat(fetchFirstPage().hasMore()).isFalse();
  }

  @Test
  @DisplayName("AC-CAT-4: Blogger paging by start-index")
  void pages_by_start_index_until_total_results_are_read() {
    bloggerFeed.stubFor(
        get(urlPathEqualTo(FEED))
            .withQueryParam("start-index", equalTo("1"))
            .willReturn(okJson(Fixtures.bloggerFeed(209, 1, 1, 150))));
    bloggerFeed.stubFor(
        get(urlPathEqualTo(FEED))
            .withQueryParam("start-index", equalTo("151"))
            .willReturn(okJson(Fixtures.bloggerFeed(209, 151, 151, 59))));

    List<SourceEntry> entries = Fixtures.fetchAll(source, ELEGANT, Optional.empty());

    assertThat(entries).hasSize(209);
    assertThat(snapshotOf(entries.getFirst()).id().external()).isEqualTo("elegant:9000001");
    assertThat(snapshotOf(entries.getLast()).id().external()).isEqualTo("elegant:9000209");
    for (String startIndex : List.of("1", "151")) {
      bloggerFeed.verify(
          1,
          getRequestedFor(urlPathEqualTo(FEED))
              .withQueryParam("alt", equalTo("json"))
              .withQueryParam("max-results", equalTo("150"))
              .withQueryParam("start-index", equalTo(startIndex)));
    }
    bloggerFeed.verify(2, getRequestedFor(urlPathEqualTo(FEED)));
  }

  @Test
  @DisplayName("AC-CAT-5: updated-min carries an explicit UTC offset")
  void sends_changed_since_with_an_explicit_utc_offset_ordered_by_update() {
    stubFeed(Fixtures.read("blogger/feed-empty.json"));

    source.fetch(ELEGANT, Optional.of(Instant.parse("2026-09-20T00:20:47Z")), PageCursor.first());

    bloggerFeed.verify(
        getRequestedFor(urlPathEqualTo(FEED))
            .withQueryParam("updated-min", matching("2026-09-20T00:20:47(\\.0+)?(Z|\\+00:00)"))
            .withQueryParam("orderby", equalTo("updated")));
  }

  @Test
  @DisplayName("AC-CAT-1: a full fetch sends no updated-min")
  void full_fetch_sends_no_changed_since() {
    stubFeed(Fixtures.read("blogger/feed-empty.json"));

    fetchFirstPage();

    bloggerFeed.verify(
        getRequestedFor(urlPathEqualTo(FEED)).withQueryParam("updated-min", absent()));
  }

  @Test
  void http_error_makes_the_source_unavailable() {
    bloggerFeed.stubFor(
        get(urlPathEqualTo(FEED)).willReturn(aResponse().withStatus(503).withBody("busy")));

    assertThatThrownBy(this::fetchFirstPage)
        .isInstanceOf(SourceUnavailable.class)
        .hasMessageContaining("503");
  }
}
