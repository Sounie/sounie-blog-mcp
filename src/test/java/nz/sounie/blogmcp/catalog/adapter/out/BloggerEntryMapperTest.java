package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.domain.TestSites.ELEGANT;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class BloggerEntryMapperTest {

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final Instant UPDATED = Instant.parse("2026-03-23T20:53:30.835Z");

  private final BloggerEntryMapper mapper =
      new BloggerEntryMapper(ELEGANT, new MarkingHtmlToText());

  /** A summary-only entry in the shape of the recorded feed. */
  private static ObjectNode entry() {
    return (ObjectNode)
        JSON.readTree(
            """
            {"id": {"$t": "tag:blogger.com,1999:blog-1481195935080195479.post-371460637286630063"},
             "published": {"$t": "2026-03-23T21:53:00.001+01:00"},
             "updated": {"$t": "2026-03-23T20:53:30.835+00:00"},
             "category": [{"term": "DDD"}, {"term": " ddd "}, {"term": "Java"}],
             "title": {"type": "text", "$t": "List<String> & co"},
             "summary": {"type": "text", "$t": "A summary"},
             "link": [
               {"rel": "replies", "href": "https://blog.elegant-solutions.london/feeds/1/comments"},
               {"rel": "alternate", "type": "text/html",
                "href": "https://blog.elegant-solutions.london/2026/03/post.html"}]}
            """);
  }

  private static PostSnapshot snapshotOf(SourceEntry entry) {
    assertThat(entry).isInstanceOf(SourceEntry.Available.class);
    return ((SourceEntry.Available) entry).snapshot();
  }

  @Test
  @DisplayName("AC-CAT-4 / AC-CAT-7: every field of an entry is mapped")
  void maps_every_field_of_an_entry() {
    PostSnapshot snapshot = snapshotOf(mapper.map(entry()));

    assertThat(snapshot.id().external()).isEqualTo("elegant:371460637286630063");
    assertThat(snapshot.url().value())
        .isEqualTo(URI.create("https://blog.elegant-solutions.london/2026/03/post.html"));
    assertThat(snapshot.publishedAt()).isEqualTo(Instant.parse("2026-03-23T20:53:00.001Z"));
    assertThat(snapshot.updatedAt()).isEqualTo(UPDATED);
  }

  @Test
  @DisplayName("Q1: without content, the summary is the body and completeness is SUMMARY")
  void uses_the_summary_when_there_is_no_content() {
    PostSnapshot snapshot = snapshotOf(mapper.map(entry()));

    assertThat(snapshot.body().text()).isEqualTo("extracted(A summary)");
    assertThat(snapshot.completeness()).isEqualTo(BodyCompleteness.SUMMARY);
  }

  @Test
  @DisplayName("Q1: content, when present, is the body and completeness is FULL")
  void uses_the_content_when_present() {
    ObjectNode entry = entry();
    entry.putObject("content").put("type", "html").put("$t", "<p>Full</p>");

    PostSnapshot snapshot = snapshotOf(mapper.map(entry));

    assertThat(snapshot.body().text()).isEqualTo("extracted(<p>Full</p>)");
    assertThat(snapshot.completeness()).isEqualTo(BodyCompleteness.FULL);
  }

  @Test
  void the_title_is_read_in_the_format_its_type_names() {
    assertThat(snapshotOf(mapper.map(entry())).title().value()).isEqualTo("List<String> & co");

    ObjectNode html = entry();
    html.putObject("title").put("type", "html").put("$t", "Hello <em>World</em>");
    assertThat(snapshotOf(mapper.map(html)).title().value())
        .isEqualTo("extracted(Hello <em>World</em>)");
  }

  @Test
  @DisplayName("AC-CAT-9: labels become tags")
  void labels_become_tags() {
    assertThat(snapshotOf(mapper.map(entry())).tags())
        .extracting(Tag::value)
        .containsExactly("DDD", "Java");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "tag:blogger.com,1999:blog-1481195935080195479", "post-123", ".post-abc"})
  @DisplayName("AC-CAT-16: an id without digits after .post- is malformed and unidentified")
  void an_id_without_a_post_number_makes_the_entry_malformed(String id) {
    ObjectNode entry = entry();
    entry.putObject("id").put("$t", id);

    assertThat(mapper.map(entry))
        .isEqualTo(
            new SourceEntry.Malformed(
                Optional.empty(), "missing or invalid id", Optional.of(UPDATED)));
  }

  @Test
  void an_unparseable_updated_time_makes_the_entry_malformed_and_undated() {
    ObjectNode entry = entry();
    entry.putObject("updated").put("$t", "2026-03-23T20:53:30");

    assertThat(mapper.map(entry))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> {
              assertThat(malformed.sourcePostId()).contains(new SourcePostId("371460637286630063"));
              assertThat(malformed.updatedAt()).isEmpty();
            });
  }

  @Test
  void an_unparseable_published_time_makes_the_entry_malformed_but_dated() {
    ObjectNode entry = entry();
    entry.remove("published");

    assertThat(mapper.map(entry))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> assertThat(malformed.updatedAt()).contains(UPDATED));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "http://blog.elegant-solutions.london/x.html", "https://evil.example/x"})
  @DisplayName("AC-CAT-16: an alternate link that is not https on the site makes it malformed")
  void an_alternate_link_off_the_site_makes_the_entry_malformed(String href) {
    ObjectNode entry = entry();
    entry.putArray("link").addObject().put("rel", "alternate").put("href", href);

    assertThat(mapper.map(entry))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> {
              assertThat(malformed.sourcePostId()).contains(new SourcePostId("371460637286630063"));
              assertThat(malformed.updatedAt()).contains(UPDATED);
            });
  }

  @Test
  void an_entry_without_an_alternate_link_is_malformed() {
    ObjectNode entry = entry();
    entry.putArray("link").addObject().put("rel", "replies").put("href", "https://x.example/");

    assertThat(mapper.map(entry)).isInstanceOf(SourceEntry.Malformed.class);
  }
}
