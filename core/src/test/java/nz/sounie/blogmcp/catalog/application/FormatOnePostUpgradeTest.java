package nz.sounie.blogmcp.catalog.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.page;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.image;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.ELEGANT_DEFINITION;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_DEFINITION;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource;
import nz.sounie.blogmcp.catalog.adapter.out.FilePostRepository;
import nz.sounie.blogmcp.catalog.adapter.out.FixedSiteDirectory;
import nz.sounie.blogmcp.catalog.adapter.out.FormatOnePostFile;
import nz.sounie.blogmcp.catalog.adapter.out.InMemorySyncCheckpointRepository;
import nz.sounie.blogmcp.catalog.adapter.out.RecordingEventPublisher;
import nz.sounie.blogmcp.catalog.domain.post.PostImage;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.shared.event.CatalogPostRevised;
import nz.sounie.blogmcp.shared.query.CatalogPostImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Q9 end to end: a post stored by the previous release (format 1, no images) is upgraded by the
 * next sync that sees its images. Real files and real Jackson; the blog source is our own port.
 */
class FormatOnePostUpgradeTest {

  private static final Instant AT = Instant.parse("2026-09-20T01:20:47Z");
  private static final String URL = "https://blog2.sounie.nz/2026/09/20/hello/";
  private static final PostImage SWAN = image("https://x.example/swan.jpg", "Swan", "");

  @TempDir Path dataDirectory;

  private final PrintStream errors = new PrintStream(new ByteArrayOutputStream(), true, UTF_8);
  private final RecordingEventPublisher events = new RecordingEventPublisher();
  private final FakeBlogSource wordPress = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);

  private SyncSite syncSiteOver(FilePostRepository posts) {
    return new SyncSite(
        FixedSiteDirectory.of(SOUNIE_WP_DEFINITION, ELEGANT_DEFINITION),
        Map.of(
            Platform.WORDPRESS,
            wordPress,
            Platform.BLOGGER,
            new FakeBlogSource(ChangeOrder.NEWEST_FIRST)),
        posts,
        new InMemorySyncCheckpointRepository(),
        events,
        Clock.fixed(AT.plusSeconds(86_400), ZoneOffset.UTC));
  }

  @Test
  @DisplayName(
      "AC-CAT-41 / Q9: images seen for a format-1 post at the same updatedAt revise it and"
          + " rewrite its file as format 2")
  void images_seen_for_a_format_1_post_revise_it_and_rewrite_the_file_as_format_2()
      throws IOException {
    Path file = FormatOnePostFile.write(dataDirectory, "sounie-wp", "123", URL, AT);
    FilePostRepository posts = FilePostRepository.open(dataDirectory, errors);
    wordPress.willServe(
        SOUNIE_WP_ID,
        page(
            new SourceEntry.Available(
                aSnapshot()
                    .sourcePostId("123")
                    .url(URL)
                    .title("Same")
                    .body("Same body.")
                    .tags("DDD")
                    .publishedAt(AT)
                    .updatedAt(AT)
                    .images(SWAN)
                    .build())));

    syncSiteOver(posts).run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

    assertThat(events.events())
        .singleElement()
        .isInstanceOfSatisfying(
            CatalogPostRevised.class,
            revised -> {
              assertThat(revised.changed()).containsExactly("IMAGES");
              assertThat(revised.images())
                  .containsExactly(new CatalogPostImage("https://x.example/swan.jpg", "Swan", ""));
            });
    JsonNode stored = JsonMapper.builder().build().readTree(Files.readString(file));
    assertThat(stored.get("format").asInt()).isEqualTo(2);
    assertThat(stored.get("images").values())
        .singleElement()
        .satisfies(
            image -> assertThat(image.get("url").asString()).isEqualTo(SWAN.url().toString()));
    assertThat(FilePostRepository.open(dataDirectory, errors).findById(aSnapshot().postId()))
        .hasValueSatisfying(post -> assertThat(post.images().asList()).containsExactly(SWAN));
  }
}
