package nz.sounie.blogmcp.catalog.adapter.out;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.post.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.PageCursor;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.sync.SourcePage;
import nz.sounie.blogmcp.catalog.domain.sync.SourceUnavailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WordPressSourceTest {

  private static final String POSTS = "/wp-json/wp/v2/posts";
  private static final String TAGS = "/wp-json/wp/v2/tags";

  @RegisterExtension
  static final WireMockExtension wordPressApi =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private WordPressSource source;

  @BeforeEach
  void setUp() {
    source =
        new WordPressSource(
            HttpClient.newHttpClient(),
            new JsoupHtmlToText(),
            site -> URI.create(wordPressApi.baseUrl()));
    stubTags(Fixtures.read("wordpress/tags-recorded.json"));
  }

  private void stubTags(String body) {
    wordPressApi.stubFor(
        get(urlPathEqualTo(TAGS))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=UTF-8")
                    .withHeader("X-WP-Total", "2")
                    .withHeader("X-WP-TotalPages", "1")
                    .withBody(body)));
  }

  private void stubSinglePageOfPosts(String body) {
    wordPressApi.stubFor(
        get(urlPathEqualTo(POSTS))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=UTF-8")
                    .withHeader("X-WP-Total", "2")
                    .withHeader("X-WP-TotalPages", "1")
                    .withBody(body)));
  }

  private SourcePage fetchFirstPage() {
    return source.fetch(SOUNIE_WP, Optional.empty(), PageCursor.first());
  }

  private static PostSnapshot snapshotOf(SourceEntry entry) {
    assertThat(entry).isInstanceOf(SourceEntry.Available.class);
    return ((SourceEntry.Available) entry).snapshot();
  }

  @Test
  void returns_changes_oldest_first() {
    assertThat(source.changeOrder()).isEqualTo(ChangeOrder.OLDEST_FIRST);
  }

  @Test
  void maps_recorded_posts_to_snapshots() {
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-recorded.json"));

    SourcePage page = fetchFirstPage();

    assertThat(page.entries()).hasSize(2);
    assertThat(page.hasMore()).isFalse();
    PostSnapshot first = snapshotOf(page.entries().getFirst());
    assertThat(first.id().external()).isEqualTo("sounie-wp:34");
    assertThat(first.url().value())
        .isEqualTo(URI.create("https://blog2.sounie.nz/what-have-i-done-lately-part-1-ai/"));
    assertThat(first.title().value()).isEqualTo("What have I done lately? – Part 1: AI");
    assertThat(first.body().text())
        .startsWith("This post is my attempt at jotting down")
        .doesNotContain("<p", "</p>", "&#8217;");
    assertThat(first.completeness()).isEqualTo(BodyCompleteness.FULL);
    assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-05-04T22:27:45Z"));
    assertThat(first.updatedAt()).isEqualTo(Instant.parse("2026-05-06T02:19:50Z"));
    assertThat(first.tags())
        .extracting(Tag::value)
        .containsExactlyInAnyOrder(
            "AI hallucination", "AI performance analysis", "AI strengths and weaknesses");
    assertThat(snapshotOf(page.entries().get(1)).id().sourcePostId())
        .isEqualTo(new SourcePostId("27"));
  }

  @Test
  @DisplayName("AC-CAT-2: paging follows X-WP-TotalPages")
  void follows_pages_up_to_the_reported_total_pages() {
    stubPostsPage(1, Fixtures.wordPressPosts(1, 100));
    stubPostsPage(2, Fixtures.wordPressPosts(101, 100));
    stubPostsPage(3, Fixtures.wordPressPosts(201, 30));

    List<SourceEntry> entries = Fixtures.fetchAll(source, SOUNIE_WP, Optional.empty());

    assertThat(entries).hasSize(230).allMatch(SourceEntry.Available.class::isInstance);
    for (int page = 1; page <= 3; page++) {
      wordPressApi.verify(
          1,
          getRequestedFor(urlPathEqualTo(POSTS))
              .withQueryParam("page", equalTo(String.valueOf(page)))
              .withQueryParam("per_page", equalTo("100"))
              .withQueryParam("orderby", equalTo("modified"))
              .withQueryParam("order", equalTo("asc")));
    }
    wordPressApi.verify(
        0, getRequestedFor(urlPathEqualTo(POSTS)).withQueryParam("page", equalTo("4")));
  }

  private void stubPostsPage(int page, String body) {
    wordPressApi.stubFor(
        get(urlPathEqualTo(POSTS))
            .withQueryParam("page", equalTo(String.valueOf(page)))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=UTF-8")
                    .withHeader("X-WP-Total", "230")
                    .withHeader("X-WP-TotalPages", "3")
                    .withBody(body)));
  }

  @Test
  @DisplayName("AC-CAT-3: an empty WordPress site needs exactly one request")
  void empty_site_needs_one_request_and_has_no_more_pages() {
    wordPressApi.stubFor(
        get(urlPathEqualTo(POSTS))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=UTF-8")
                    .withHeader("X-WP-Total", "0")
                    .withHeader("X-WP-TotalPages", "0")
                    .withBody("[]")));

    List<SourceEntry> entries = Fixtures.fetchAll(source, SOUNIE_WP, Optional.empty());

    assertThat(entries).isEmpty();
    wordPressApi.verify(1, getRequestedFor(urlPathEqualTo(POSTS)));
  }

  @Test
  @DisplayName("AC-CAT-5: modified_after carries an explicit UTC offset")
  void sends_changed_since_with_an_explicit_utc_offset() {
    stubSinglePageOfPosts("[]");

    source.fetch(SOUNIE_WP, Optional.of(Instant.parse("2026-09-20T00:20:47Z")), PageCursor.first());

    wordPressApi.verify(
        getRequestedFor(urlPathEqualTo(POSTS))
            .withQueryParam("modified_after", matching("2026-09-20T00:20:47(\\.0+)?(Z|\\+00:00)")));
  }

  @Test
  @DisplayName("AC-CAT-1: a full fetch sends no modified_after")
  void full_fetch_sends_no_changed_since() {
    stubSinglePageOfPosts("[]");

    fetchFirstPage();

    wordPressApi.verify(
        getRequestedFor(urlPathEqualTo(POSTS)).withQueryParam("modified_after", absent()));
  }

  @Test
  @DisplayName("AC-CAT-6: title entities are decoded and content becomes plain text")
  void decodes_title_entities_and_extracts_plain_text_from_content() {
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-html-sample.json"));

    PostSnapshot snapshot = snapshotOf(fetchFirstPage().entries().getFirst());

    assertThat(snapshot.title().value()).isEqualTo("Don’t & <panic>");
    assertThat(snapshot.body().text())
        .containsSubsequence("One line", "\n\n", "  a\n  b", "Two")
        .doesNotContain("x()", "<p>", "<pre>", "&nbsp;");
  }

  @Test
  @DisplayName("AC-CAT-7: zone-less WordPress GMT timestamps are UTC")
  void reads_zone_less_gmt_timestamps_as_utc() {
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-html-sample.json"));

    PostSnapshot snapshot = snapshotOf(fetchFirstPage().entries().getFirst());

    assertThat(snapshot.publishedAt()).isEqualTo(Instant.parse("2026-09-20T01:20:47Z"));
    assertThat(snapshot.updatedAt()).isEqualTo(Instant.parse("2026-09-20T01:20:47Z"));
  }

  @Test
  @DisplayName("AC-CAT-8: tag IDs resolve to names; unknown IDs are noted; categories ignored")
  void resolves_tag_ids_notes_unknown_ones_and_never_requests_categories() {
    stubTags(Fixtures.read("wordpress/tags-java.json"));
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-tags-and-categories.json"));

    SourceEntry entry = fetchFirstPage().entries().getFirst();

    assertThat(entry).isInstanceOf(SourceEntry.Available.class);
    SourceEntry.Available available = (SourceEntry.Available) entry;
    assertThat(available.snapshot().tags()).extracting(Tag::value).containsExactly("java");
    assertThat(available.notes()).anySatisfy(note -> assertThat(note).contains("99"));
    wordPressApi.verify(moreThanOrExactly(1), getRequestedFor(urlPathEqualTo(TAGS)));
    wordPressApi.verify(0, getRequestedFor(urlPathMatching("/wp-json/wp/v2/categories.*")));
  }

  @Test
  @DisplayName("AC-CAT-15: a password-protected post is not public")
  void password_protected_post_is_not_public() {
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-protected.json"));

    SourcePage page = fetchFirstPage();

    assertThat(page.entries().getFirst()).isInstanceOf(SourceEntry.Available.class);
    assertThat(page.entries().get(1))
        .isEqualTo(
            new SourceEntry.NotPublic(
                new SourcePostId("27"), Optional.of(Instant.parse("2026-05-06T02:20:21Z"))));
  }

  @Test
  @DisplayName("AC-CAT-16: a malformed entry is reported and the rest of the page is mapped")
  void malformed_entry_is_reported_with_its_id_and_the_rest_are_mapped() {
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-malformed-http-link.json"));

    List<SourceEntry> entries = fetchFirstPage().entries();

    assertThat(entries).hasSize(3);
    assertThat(entries.getFirst()).isInstanceOf(SourceEntry.Available.class);
    assertThat(entries.get(2)).isInstanceOf(SourceEntry.Available.class);
    assertThat(entries.get(1))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> {
              assertThat(malformed.sourcePostId()).contains(new SourcePostId("30"));
              assertThat(malformed.updatedAt()).contains(Instant.parse("2026-05-06T02:20:00Z"));
            });
  }

  @Test
  @DisplayName("AC-CAT-6: a title with markup becomes plain text")
  void title_markup_is_stripped_and_entities_decoded() {
    stubSinglePageOfPosts(Fixtures.read("wordpress/posts-title-markup.json"));

    PostSnapshot snapshot = snapshotOf(fetchFirstPage().entries().getFirst());

    assertThat(snapshot.title().value()).isEqualTo("Hello World & more");
  }

  static Stream<Arguments> nonArrayBodies() {
    return Stream.of(
        Arguments.of("application/json", "{}"),
        Arguments.of("text/html", "<html><body><h1>Maintenance</h1></body></html>"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("nonArrayBodies")
  @DisplayName(
      "§3.4 no false completeness: a 200 response that is not a JSON array is SourceUnavailable")
  void a_successful_response_that_is_not_a_json_array_makes_the_source_unavailable(
      String contentType, String body) {
    wordPressApi.stubFor(
        get(urlPathEqualTo(POSTS))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", contentType)
                    .withHeader("X-WP-Total", "0")
                    .withHeader("X-WP-TotalPages", "0")
                    .withBody(body)));

    assertThatThrownBy(this::fetchFirstPage).isInstanceOf(SourceUnavailable.class);
  }

  private void stubPostsPageWithTotalPagesHeader(int page, String body, String totalPages) {
    var response =
        aResponse().withHeader("Content-Type", "application/json; charset=UTF-8").withBody(body);
    if (totalPages != null) {
      response = response.withHeader("X-WP-TotalPages", totalPages);
    }
    wordPressApi.stubFor(
        get(urlPathEqualTo(POSTS))
            .withQueryParam("page", equalTo(String.valueOf(page)))
            .willReturn(response));
  }

  @Test
  @DisplayName(
      "§3.4 no false completeness: without X-WP-TotalPages, paging continues while pages are full")
  void without_a_total_pages_header_keeps_paging_until_a_short_page() {
    stubPostsPageWithTotalPagesHeader(1, Fixtures.wordPressPosts(1, 100), null);
    stubPostsPageWithTotalPagesHeader(2, Fixtures.wordPressPosts(101, 100), null);
    stubPostsPageWithTotalPagesHeader(3, Fixtures.wordPressPosts(201, 30), null);

    List<SourceEntry> entries = Fixtures.fetchAll(source, SOUNIE_WP, Optional.empty());

    assertThat(entries).hasSize(230);
    wordPressApi.verify(3, getRequestedFor(urlPathEqualTo(POSTS)));
  }

  @Test
  void http_error_makes_the_source_unavailable() {
    wordPressApi.stubFor(
        get(urlPathEqualTo(POSTS)).willReturn(aResponse().withStatus(503).withBody("busy")));

    assertThatThrownBy(this::fetchFirstPage)
        .isInstanceOf(SourceUnavailable.class)
        .hasMessageContaining("503");
  }
}
