package nz.sounie.blogmcp.catalog.adapter.out;

import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.post.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class WordPressPostMapperTest {

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final Instant MODIFIED = Instant.parse("2026-09-20T01:20:47Z");
  private static final Instant PUBLISHED = Instant.parse("2026-09-19T08:00:00Z");

  private final WordPressPostMapper mapper =
      new WordPressPostMapper(SOUNIE_WP, Map.of(5L, "java", 7L, "Java"), new MarkingHtmlToText());

  /** A complete, public WordPress post that maps to an available entry. */
  private static ObjectNode post() {
    return (ObjectNode)
        JSON.readTree(
            """
            {"id": 123, "date_gmt": "2026-09-19T08:00:00", "modified_gmt": "2026-09-20T01:20:47",
             "link": "https://blog2.sounie.nz/hello/", "status": "publish", "type": "post",
             "title": {"rendered": "Hello &amp; welcome"},
             "content": {"rendered": "<p>Body</p>", "protected": false},
             "tags": [5, 7], "categories": [1, 3]}
            """);
  }

  private SourceEntry map(JsonNode post) {
    return mapper.map(post);
  }

  private static PostSnapshot snapshotOf(SourceEntry entry) {
    assertThat(entry).isInstanceOf(SourceEntry.Available.class);
    return ((SourceEntry.Available) entry).snapshot();
  }

  @Test
  @DisplayName("AC-CAT-6 / AC-CAT-7: every field of a public post is mapped")
  void maps_every_field_of_a_public_post() {
    PostSnapshot snapshot = snapshotOf(map(post()));

    assertThat(snapshot.id().external()).isEqualTo("sounie-wp:123");
    assertThat(snapshot.url().value()).isEqualTo(URI.create("https://blog2.sounie.nz/hello/"));
    assertThat(snapshot.title().value()).isEqualTo("extracted(Hello &amp; welcome)");
    assertThat(snapshot.body().text()).isEqualTo("extracted(<p>Body</p>)");
    assertThat(snapshot.completeness()).isEqualTo(BodyCompleteness.FULL);
    assertThat(snapshot.publishedAt()).isEqualTo(PUBLISHED);
    assertThat(snapshot.updatedAt()).isEqualTo(MODIFIED);
  }

  @Test
  @DisplayName("AC-CAT-8: tag IDs resolve to names, de-duplicated ignoring case")
  void resolves_tag_ids_to_names() {
    assertThat(snapshotOf(map(post())).tags()).extracting(Tag::value).containsExactly("java");
  }

  @Test
  @DisplayName("AC-CAT-8: an unknown tag ID is dropped and noted, not skipped")
  void notes_and_drops_an_unknown_tag_id() {
    ObjectNode post = post();
    post.putArray("tags").add(5).add(99);

    SourceEntry entry = map(post);

    assertThat(snapshotOf(entry).tags()).extracting(Tag::value).containsExactly("java");
    assertThat(((SourceEntry.Available) entry).notes())
        .singleElement()
        .satisfies(note -> assertThat(note).contains("99"));
  }

  @Test
  @DisplayName("AC-CAT-8: categories contribute nothing")
  void ignores_categories() {
    ObjectNode post = post();
    post.putArray("tags");

    assertThat(snapshotOf(map(post)).tags()).isEmpty();
  }

  @Test
  void accepts_a_string_id() {
    ObjectNode post = post();
    post.put("id", "123");

    assertThat(snapshotOf(map(post)).id().sourcePostId()).isEqualTo(new SourcePostId("123"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "blank", "object"})
  @DisplayName("AC-CAT-16: an entry without a readable id is malformed and unidentified")
  void an_unreadable_id_makes_the_entry_malformed(String variant) {
    ObjectNode post = post();
    switch (variant) {
      case "missing" -> post.remove("id");
      case "blank" -> post.put("id", " ");
      default -> post.putObject("id");
    }

    assertThat(map(post))
        .isEqualTo(
            new SourceEntry.Malformed(
                Optional.empty(), "missing or invalid id", Optional.of(MODIFIED)));
  }

  @Test
  @DisplayName("AC-CAT-16: an unparseable modified_gmt makes the entry malformed and undated")
  void an_unparseable_modified_time_makes_the_entry_malformed() {
    ObjectNode post = post();
    post.put("modified_gmt", "not-a-date");

    assertThat(map(post))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> {
              assertThat(malformed.sourcePostId()).contains(new SourcePostId("123"));
              assertThat(malformed.updatedAt()).isEmpty();
            });
  }

  @Test
  void an_unparseable_date_gmt_makes_the_entry_malformed_but_dated() {
    ObjectNode post = post();
    post.remove("date_gmt");

    assertThat(map(post))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> {
              assertThat(malformed.sourcePostId()).contains(new SourcePostId("123"));
              assertThat(malformed.updatedAt()).contains(MODIFIED);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://blog2.sounie.nz/x", "https://evil.example/x", ""})
  @DisplayName("AC-CAT-16: a link that is not https on the site makes the entry malformed")
  void a_link_off_the_site_makes_the_entry_malformed(String link) {
    ObjectNode post = post();
    post.put("link", link);

    assertThat(map(post))
        .isInstanceOfSatisfying(
            SourceEntry.Malformed.class,
            malformed -> {
              assertThat(malformed.sourcePostId()).contains(new SourcePostId("123"));
              assertThat(malformed.updatedAt()).contains(MODIFIED);
              assertThat(malformed.reason()).isNotBlank();
            });
  }

  @Test
  @DisplayName("AC-CAT-15: a password-protected post is not public")
  void a_password_protected_post_is_not_public() {
    ObjectNode post = post();
    ((ObjectNode) post.get("content")).put("protected", true);

    assertThat(map(post))
        .isEqualTo(new SourceEntry.NotPublic(new SourcePostId("123"), Optional.of(MODIFIED)));
  }

  @Test
  void a_post_whose_status_is_not_publish_is_not_public() {
    ObjectNode post = post();
    post.put("status", "private");

    assertThat(map(post)).isInstanceOf(SourceEntry.NotPublic.class);
  }
}
