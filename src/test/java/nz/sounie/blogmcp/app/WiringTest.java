package nz.sounie.blogmcp.app;

import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.failure;
import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.page;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_DEFINITION;
import static nz.sounie.blogmcp.catalog.domain.site.TestSites.SOUNIE_WP_ID;
import static nz.sounie.blogmcp.search.domain.query.SearchQueryBuilder.aQuery;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource;
import nz.sounie.blogmcp.catalog.adapter.out.FixedSiteDirectory;
import nz.sounie.blogmcp.catalog.adapter.out.JsonFileSiteDirectory;
import nz.sounie.blogmcp.catalog.adapter.out.StorageHealth;
import nz.sounie.blogmcp.catalog.application.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The composition root over real files in a temporary data directory, with fakes of our own ports
 * for the blogs ({@code BlogSource}) and the model ({@code PassageEmbedder}, {@code QueryEmbedder},
 * {@code TokenCounter}).
 */
class WiringTest {

  private static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");
  private static final SiteDirectory ONE_SITE = FixedSiteDirectory.of(SOUNIE_WP_DEFINITION);

  @TempDir Path dataDirectory;

  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(stderr, true, StandardCharsets.UTF_8);

  private static SourceEntry recordsPost() {
    return new SourceEntry.Available(
        aSnapshot()
            .sourcePostId("1")
            .title("Records in Java")
            .body("Records in Java are transparent carriers for immutable data.")
            .publishedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build());
  }

  private static FakeBlogSource wordPressServing(FakeBlogSource.Step... steps) {
    FakeBlogSource source = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);
    source.willServe(SOUNIE_WP_ID, steps);
    return source;
  }

  private Wiring wiring(FakeBlogSource wordPress, FakeEmbedder embedder, Instant now) {
    return wiring(ONE_SITE, wordPress, embedder, now);
  }

  private Wiring wiring(
      SiteDirectory sites, FakeBlogSource wordPress, FakeEmbedder embedder, Instant now) {
    Adapters adapters =
        new Adapters(
            Map.of(
                Platform.WORDPRESS,
                wordPress,
                Platform.BLOGGER,
                new FakeBlogSource(ChangeOrder.NEWEST_FIRST)),
            embedder,
            embedder,
            FakeTokenCounter.perWord(1),
            Clock.fixed(now, ZoneOffset.UTC));
    return Wiring.assemble(dataDirectory, sites, adapters, errors);
  }

  /** A data directory holding the records post, its checkpoint and its index entry. */
  private void givenAPersistedSyncAt(Instant now) {
    wiring(wordPressServing(page(recordsPost())), new FakeEmbedder(), now).job().run();
  }

  private static List<String> found(Wiring wiring, String query) {
    return wiring.searchPosts().search(aQuery(query).build()).matches().stream()
        .map(PostMatch::postId)
        .map(id -> id.external())
        .toList();
  }

  private static long jsonFilesIn(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return 0;
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.toString().endsWith(".json")).count();
    }
  }

  @Test
  @DisplayName(
      "AC-APP-9: from an empty data directory, the startup run makes every post searchable")
  void the_first_run_syncs_indexes_and_persists_everything() throws IOException {
    Wiring wiring = wiring(wordPressServing(page(recordsPost())), new FakeEmbedder(), NOW);

    wiring.job().run();

    assertThat(found(wiring, "records")).containsExactly("sounie-wp:1");
    assertThat(wiring.getPost().byId("sounie-wp:1")).isPresent();
    assertThat(jsonFilesIn(dataDirectory.resolve("catalog/posts/sounie-wp"))).isEqualTo(1);
    assertThat(dataDirectory.resolve("catalog/checkpoints/sounie-wp.json")).isRegularFile();
    assertThat(jsonFilesIn(dataDirectory.resolve("search/index/sounie-wp"))).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "AC-APP-9: after a restart, stored data is served at once and nothing is re-embedded")
  void a_restart_serves_stored_data_before_the_run_and_re_embeds_nothing() {
    givenAPersistedSyncAt(NOW);
    FakeEmbedder embedder = new FakeEmbedder();
    Wiring restarted =
        wiring(wordPressServing(failure("connection refused")), embedder, NOW.plusSeconds(60));

    assertThat(found(restarted, "records")).containsExactly("sounie-wp:1");
    assertThat(restarted.getPost().byId("sounie-wp:1")).isPresent();

    restarted.job().run();

    assertThat(embedder.passageCallCount()).as("passages embedded by the reconcile").isZero();
    assertThat(found(restarted, "records")).containsExactly("sounie-wp:1");
  }

  @ParameterizedTest
  @CsvSource({"false, HEALTHY, true", "true, DAMAGED, false"})
  @DisplayName(
      "AC-APP-9: the startup run is INCREMENTAL when storage is healthy, RECONCILE when damaged")
  void the_startup_run_mode_follows_storage_health(
      boolean corruptPostFile, StorageHealth expectedHealth, boolean incremental)
      throws IOException {
    givenAPersistedSyncAt(NOW);
    if (corruptPostFile) {
      Files.writeString(dataDirectory.resolve("catalog/posts/sounie-wp/garbage.json"), "{not json");
    }
    FakeBlogSource wordPress = wordPressServing(page(recordsPost()));
    Wiring restarted = wiring(wordPress, new FakeEmbedder(), NOW.plus(Duration.ofHours(1)));

    assertThat(restarted.storageHealth()).isEqualTo(expectedHealth);
    restarted.job().run();

    assertThat(wordPress.requestsFor(SOUNIE_WP_ID))
        .first()
        .satisfies(
            request ->
                assertThat(request.changedSince().isPresent())
                    .as("incremental (changedSince present)")
                    .isEqualTo(incremental));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"'' | 24", "'\"syncEveryHours\": 6,' | 6"})
  @DisplayName(
      "AC-APP-10: the job runs now and then every syncEveryHours (default 24) as a fixed delay")
  void schedules_the_job_with_the_configured_interval(String setting, long hours)
      throws IOException {
    Path config =
        Files.writeString(
            dataDirectory.resolve("sites.json"),
            """
            {%s "sites": [
              {"id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://blog2.sounie.nz"}
            ]}
            """
                .formatted(setting));
    JsonFileSiteDirectory sites = new JsonFileSiteDirectory(config);
    FakeBlogSource wordPress = wordPressServing(page(recordsPost()));
    Wiring wiring = wiring(sites, wordPress, new FakeEmbedder(), NOW);
    RecordingJobTimer timer = new RecordingJobTimer();

    wiring.schedule(timer, sites.load().syncInterval());

    assertThat(timer.requests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.delay()).isEqualTo(Duration.ofHours(hours));
              request.job().run();
            });
    assertThat(wordPress.requestsFor(SOUNIE_WP_ID)).as("the scheduled job syncs").isNotEmpty();
    assertThat(found(wiring, "records")).as("and reconciles").containsExactly("sounie-wp:1");
  }
}
