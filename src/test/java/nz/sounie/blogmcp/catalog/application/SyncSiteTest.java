package nz.sounie.blogmcp.catalog.application;

import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.failure;
import static nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource.page;
import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static nz.sounie.blogmcp.catalog.domain.TestSites.ELEGANT;
import static nz.sounie.blogmcp.catalog.domain.TestSites.ELEGANT_DEFINITION;
import static nz.sounie.blogmcp.catalog.domain.TestSites.ELEGANT_ID;
import static nz.sounie.blogmcp.catalog.domain.TestSites.SOUNIE_WP_DEFINITION;
import static nz.sounie.blogmcp.catalog.domain.TestSites.SOUNIE_WP_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource;
import nz.sounie.blogmcp.catalog.adapter.out.FixedSiteDirectory;
import nz.sounie.blogmcp.catalog.adapter.out.InMemoryPostRepository;
import nz.sounie.blogmcp.catalog.adapter.out.InMemorySyncCheckpointRepository;
import nz.sounie.blogmcp.catalog.adapter.out.RecordingEventPublisher;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.Platform;
import nz.sounie.blogmcp.catalog.domain.Post;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder;
import nz.sounie.blogmcp.catalog.domain.SiteId;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.Title;
import nz.sounie.blogmcp.shared.event.CatalogPostPublished;
import nz.sounie.blogmcp.shared.event.CatalogPostRevised;
import nz.sounie.blogmcp.shared.event.CatalogPostWithdrawn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SyncSiteTest {

  private static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");
  private static final Instant T1 = Instant.parse("2026-09-20T01:00:00Z");
  private static final Instant T2 = T1.plus(Duration.ofHours(1));
  private static final Instant T3 = T2.plus(Duration.ofHours(1));
  private static final Instant T4 = T3.plus(Duration.ofHours(1));
  private static final Instant T5 = T4.plus(Duration.ofHours(1));

  private final InMemoryPostRepository posts = new InMemoryPostRepository();
  private final InMemorySyncCheckpointRepository checkpoints =
      new InMemorySyncCheckpointRepository();
  private final RecordingEventPublisher events = new RecordingEventPublisher();
  private final FakeBlogSource wordPress = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);
  private FakeBlogSource blogger = new FakeBlogSource(ChangeOrder.NEWEST_FIRST);
  private SyncSite syncSite = newSyncSite();

  private SyncSite newSyncSite() {
    return new SyncSite(
        FixedSiteDirectory.of(SOUNIE_WP_DEFINITION, ELEGANT_DEFINITION),
        Map.of(Platform.WORDPRESS, wordPress, Platform.BLOGGER, blogger),
        posts,
        checkpoints,
        events,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  // --- test data ---------------------------------------------------------------------------

  private static PostSnapshotBuilder wpPost(String sourcePostId, Instant updatedAt) {
    return aSnapshot()
        .sourcePostId(sourcePostId)
        .title("Post " + sourcePostId)
        .updatedAt(updatedAt);
  }

  private static PostSnapshotBuilder elegantPost(String sourcePostId, Instant updatedAt) {
    return aSnapshot()
        .on(ELEGANT)
        .sourcePostId(sourcePostId)
        .title("Post " + sourcePostId)
        .updatedAt(updatedAt);
  }

  private static SourceEntry available(PostSnapshotBuilder snapshot) {
    return new SourceEntry.Available(snapshot.build());
  }

  private static SourceEntry malformed(String sourcePostId, Instant updatedAt) {
    return new SourceEntry.Malformed(
        Optional.of(new SourcePostId(sourcePostId)), "unparseable entry", Optional.of(updatedAt));
  }

  private static SourceEntry malformedWithoutId(Instant updatedAt) {
    return new SourceEntry.Malformed(Optional.empty(), "missing id", Optional.of(updatedAt));
  }

  private static PostId wpId(String sourcePostId) {
    return new PostId(SOUNIE_WP_ID, new SourcePostId(sourcePostId));
  }

  private void givenStored(PostSnapshotBuilder snapshot) {
    posts.save(snapshot.buildStoredPost());
  }

  private void givenCheckpoint(SiteId siteId, Instant changesSeenUpTo, Instant lastReconciledAt) {
    checkpoints.given(
        SyncCheckpoint.restore(
            siteId, Optional.ofNullable(changesSeenUpTo), Optional.ofNullable(lastReconciledAt)));
  }

  private Optional<Instant> checkpointOf(SiteId siteId) {
    return checkpoints.find(siteId).flatMap(SyncCheckpoint::changesSeenUpTo);
  }

  private Optional<Instant> lastReconciledAtOf(SiteId siteId) {
    return checkpoints.find(siteId).flatMap(SyncCheckpoint::lastReconciledAt);
  }

  private List<String> publishedPostIds() {
    return events.eventsOfType(CatalogPostPublished.class).stream()
        .map(CatalogPostPublished::postId)
        .toList();
  }

  // --- incremental sync and paging -----------------------------------------------------------

  @Nested
  class IncrementalSync {

    @Test
    @DisplayName("AC-CAT-1: first sync with no checkpoint fetches everything")
    void first_sync_without_checkpoint_fetches_everything_and_publishes_every_post() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1)), available(wpPost("2", T2)), available(wpPost("3", T3))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(wordPress.requests()).hasSize(1);
      assertThat(wordPress.requests().getFirst().changedSince()).isEmpty();
      assertThat(posts.findIdsBySite(SOUNIE_WP_ID))
          .containsExactlyInAnyOrder(wpId("1"), wpId("2"), wpId("3"));
      assertThat(publishedPostIds()).containsExactly("sounie-wp:1", "sounie-wp:2", "sounie-wp:3");
      assertThat(checkpointOf(SOUNIE_WP_ID)).contains(T3);
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
      assertThat(report.published()).isEqualTo(3);
      assertThat(report.mode()).isEqualTo(SyncMode.INCREMENTAL);
      assertThat(report.pagesFetched()).isEqualTo(1);
      assertThat(report.checkpointBefore()).isEmpty();
      assertThat(report.checkpointAfter()).contains(T3);
    }

    @Test
    void published_integration_event_carries_the_full_post_state() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(
              available(
                  aSnapshot()
                      .sourcePostId("123")
                      .url("https://blog2.sounie.nz/2026/09/20/hello/")
                      .title("Hello")
                      .body("Hello, world.")
                      .tags("DDD")
                      .publishedAt(T1)
                      .updatedAt(T2))));

      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(events.events())
          .containsExactly(
              new CatalogPostPublished(
                  "sounie-wp:123",
                  "sounie-wp",
                  "https://blog2.sounie.nz/2026/09/20/hello/",
                  "Hello",
                  "Hello, world.",
                  "FULL",
                  Set.of("DDD"),
                  T1,
                  T2));
    }

    @Test
    void each_event_is_published_only_after_its_post_is_saved() {
      List<Boolean> storedWhenPublished = new ArrayList<>();
      events.onPublish(
          event ->
              storedWhenPublished.add(posts.isStored(((CatalogPostPublished) event).postId())));
      wordPress.willServe(
          SOUNIE_WP_ID, page(available(wpPost("1", T1)), available(wpPost("2", T2))));

      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(storedWhenPublished).containsExactly(true, true);
    }

    @Test
    @DisplayName("AC-CAT-2: every page is followed until the source has no more")
    void follows_pages_until_the_source_has_no_more() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1))),
          page(available(wpPost("2", T2))),
          page(available(wpPost("3", T3))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(wordPress.requests()).extracting(r -> r.cursor().value()).containsExactly(1, 2, 3);
      assertThat(posts.size()).isEqualTo(3);
      assertThat(report.pagesFetched()).isEqualTo(3);
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
    }

    @Test
    @DisplayName("AC-CAT-3: an empty site completes without posts, events or checkpoint")
    void empty_site_completes_without_posts_events_or_checkpoint() {
      wordPress.willServe(SOUNIE_WP_ID, page());

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(wordPress.requests()).hasSize(1);
      assertThat(posts.size()).isZero();
      assertThat(events.events()).isEmpty();
      assertThat(checkpointOf(SOUNIE_WP_ID)).isEmpty();
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
    }

    @Test
    @DisplayName("AC-CAT-4: a newest-first source saves the checkpoint once, after the last page")
    void newest_first_source_saves_checkpoint_once_after_the_last_page() {
      blogger.willServe(
          ELEGANT_ID,
          page(available(elegantPost("30", T3)), available(elegantPost("20", T2))),
          page(available(elegantPost("10", T1))));

      SyncReport report = syncSite.run(ELEGANT_ID, SyncMode.INCREMENTAL);

      assertThat(blogger.requests()).hasSize(2);
      assertThat(posts.size()).isEqualTo(3);
      assertThat(checkpoints.saveHistory(ELEGANT_ID))
          .singleElement()
          .satisfies(saved -> assertThat(saved.changesSeenUpTo()).contains(T3));
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
    }

    @Test
    @DisplayName("AC-CAT-18: an oldest-first source saves the checkpoint after each page")
    void oldest_first_source_saves_checkpoint_after_each_page() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1)), available(wpPost("2", T2))),
          page(available(wpPost("3", T3))));

      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(checkpoints.saveHistory(SOUNIE_WP_ID))
          .extracting(SyncCheckpoint::changesSeenUpTo)
          .containsExactly(Optional.of(T2), Optional.of(T3));
    }

    @Test
    @DisplayName("AC-CAT-5: incremental sync asks for changes since checkpoint minus 1 hour")
    void incremental_sync_asks_for_changes_since_checkpoint_minus_overlap_margin() {
      givenCheckpoint(SOUNIE_WP_ID, Instant.parse("2026-09-20T01:20:47Z"), NOW);
      givenStored(wpPost("9", T1));
      wordPress.willServe(
          SOUNIE_WP_ID, page(available(wpPost("10", Instant.parse("2026-09-20T02:00:00Z")))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(wordPress.requests().getFirst().changedSince())
          .contains(Instant.parse("2026-09-20T00:20:47Z"));
      assertThat(posts.findIdsBySite(SOUNIE_WP_ID))
          .containsExactlyInAnyOrder(wpId("9"), wpId("10"));
      assertThat(publishedPostIds()).containsExactly("sounie-wp:10");
      assertThat(report.withdrawn()).isZero();
    }

    @Test
    @DisplayName("AC-CAT-8: a dropped tag reference is noted in the report, not skipped")
    void source_notes_are_reported_without_skipping_the_entry() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(
              new SourceEntry.Available(
                  wpPost("1", T1).tags("java").build(), List.of("unknown tag id 99"))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(report.published()).isEqualTo(1);
      assertThat(report.skipped()).isEmpty();
      assertThat(report.warnings())
          .singleElement()
          .satisfies(
              warning -> {
                assertThat(warning.kind()).isEqualTo(SyncWarning.Kind.SOURCE_NOTE);
                assertThat(warning.sourcePostId()).contains(new SourcePostId("1"));
                assertThat(warning.detail()).contains("99");
              });
    }

    @Test
    void rejects_a_site_that_is_not_configured() {
      assertThatThrownBy(() -> syncSite.run(new SiteId("unknown"), SyncMode.INCREMENTAL))
          .isInstanceOf(UnknownSite.class);
    }
  }

  // --- change detection ----------------------------------------------------------------------

  @Nested
  class ChangeDetection {

    @Test
    @DisplayName("AC-CAT-10: an unchanged post seen again raises no event")
    void unchanged_post_raises_no_event_and_is_counted_as_unchanged() {
      givenStored(wpPost("123", T1));
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("123", T1))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(events.events()).isEmpty();
      assertThat(report.unchanged()).isEqualTo(1);
      assertThat(report.published()).isZero();
      assertThat(report.revised()).isZero();
    }

    @Test
    @DisplayName("AC-CAT-11: a revised post raises exactly one PostRevised")
    void revised_post_raises_exactly_one_PostRevised_and_is_stored() {
      givenStored(wpPost("123", T1).title("Old"));
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("123", T2).title("New"))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(events.events())
          .singleElement()
          .isInstanceOfSatisfying(
              CatalogPostRevised.class,
              revised -> {
                assertThat(revised.postId()).isEqualTo("sounie-wp:123");
                assertThat(revised.changed()).containsExactly("TITLE");
                assertThat(revised.title()).isEqualTo("New");
              });
      Post stored = posts.findById(wpId("123")).orElseThrow();
      assertThat(stored.title()).isEqualTo(new Title("New"));
      assertThat(stored.updatedAt()).isEqualTo(T2);
      assertThat(report.revised()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-CAT-12: a newer timestamp without material change is stored without event")
    void touched_post_stores_the_new_timestamp_without_an_event() {
      givenStored(wpPost("123", T1));
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("123", T2))));

      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(events.events()).isEmpty();
      assertThat(posts.findById(wpId("123")).orElseThrow().updatedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("AC-CAT-13: a stale snapshot is ignored")
    void stale_snapshot_leaves_the_stored_post_unchanged() {
      givenStored(wpPost("123", T2).title("Current"));
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("123", T1).title("Older"))));

      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      Post stored = posts.findById(wpId("123")).orElseThrow();
      assertThat(stored.title()).isEqualTo(new Title("Current"));
      assertThat(stored.updatedAt()).isEqualTo(T2);
      assertThat(events.events()).isEmpty();
    }

    @Test
    @DisplayName("AC-CAT-14: upgrading a summary to the full body publishes one PostRevised")
    void summary_upgraded_to_full_body_publishes_PostRevised_with_body_and_completeness() {
      givenStored(
          elegantPost("371460637286630063", T1)
              .body("Short summary")
              .completeness(BodyCompleteness.SUMMARY));
      blogger.willServe(
          ELEGANT_ID,
          page(
              available(
                  elegantPost("371460637286630063", T1)
                      .body("Short summary, and the rest of the full post.")
                      .completeness(BodyCompleteness.FULL))));

      syncSite.run(ELEGANT_ID, SyncMode.INCREMENTAL);

      assertThat(events.eventsOfType(CatalogPostRevised.class))
          .singleElement()
          .satisfies(
              revised -> {
                assertThat(revised.postId()).isEqualTo("elegant:371460637286630063");
                assertThat(revised.changed()).containsExactlyInAnyOrder("BODY", "COMPLETENESS");
                assertThat(revised.completeness()).isEqualTo("FULL");
              });
    }
  }

  // --- exclusions, invalid data and failure ------------------------------------------------

  @Nested
  class ExclusionsAndFailures {

    @Test
    @DisplayName("AC-CAT-15: an unknown password-protected post is skipped as NOT_PUBLIC")
    void unknown_not_public_post_is_skipped_and_not_stored() {
      wordPress.willServe(
          SOUNIE_WP_ID, page(new SourceEntry.NotPublic(new SourcePostId("77"), Optional.of(T1))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.size()).isZero();
      assertThat(events.events()).isEmpty();
      assertThat(report.skipped())
          .containsExactly(
              new SkippedEntry(
                  Optional.of(new SourcePostId("77")),
                  SkipReason.NOT_PUBLIC,
                  report.skipped().getFirst().detail()));
    }

    @Test
    @DisplayName("AC-CAT-15: a stored post that becomes password-protected is withdrawn")
    void stored_post_that_is_no_longer_public_is_withdrawn() {
      givenStored(wpPost("77", T1).url("https://blog2.sounie.nz/secret/"));
      wordPress.willServe(
          SOUNIE_WP_ID, page(new SourceEntry.NotPublic(new SourcePostId("77"), Optional.of(T2))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.findById(wpId("77"))).isEmpty();
      assertThat(events.events())
          .containsExactly(
              new CatalogPostWithdrawn(
                  "sounie-wp:77",
                  "sounie-wp",
                  "https://blog2.sounie.nz/secret/",
                  "NO_LONGER_PUBLIC"));
      assertThat(report.withdrawn()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-CAT-16: a malformed entry is skipped and reported without aborting")
    void malformed_entry_is_skipped_and_the_rest_of_the_page_is_applied() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1)), malformed("30", T2), available(wpPost("3", T3))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).containsExactlyInAnyOrder(wpId("1"), wpId("3"));
      assertThat(publishedPostIds()).containsExactly("sounie-wp:1", "sounie-wp:3");
      assertThat(report.skipped())
          .singleElement()
          .satisfies(
              skipped -> {
                assertThat(skipped.sourcePostId()).contains(new SourcePostId("30"));
                assertThat(skipped.reason()).isEqualTo(SkipReason.MALFORMED);
                assertThat(skipped.detail()).isNotBlank();
              });
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
      assertThat(checkpointOf(SOUNIE_WP_ID)).contains(T3);
    }

    @Test
    @DisplayName(
        "AC-CAT-16: a malformed entry with a later timestamp still advances the checkpoint")
    void malformed_entry_timestamp_counts_towards_the_checkpoint() {
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("1", T1)), malformed("30", T3)));

      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(checkpointOf(SOUNIE_WP_ID)).contains(T3);
    }

    @Test
    @DisplayName(
        "AC-CAT-16: an available entry whose URL is not on the site is skipped as malformed")
    void available_entry_with_url_off_the_site_is_skipped_as_malformed() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(
              available(wpPost("1", T1).url("https://evil.example/x")),
              available(wpPost("2", T2))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).containsExactly(wpId("2"));
      assertThat(report.skipped())
          .singleElement()
          .satisfies(
              skipped -> {
                assertThat(skipped.sourcePostId()).contains(new SourcePostId("1"));
                assertThat(skipped.reason()).isEqualTo(SkipReason.MALFORMED);
              });
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
    }

    @Test
    @DisplayName("AC-CAT-17: an entry with another post's canonical URL is skipped")
    void entry_with_a_canonical_url_held_by_another_post_is_skipped() {
      givenStored(wpPost("123", T1).url("https://blog2.sounie.nz/a/").title("Original"));
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("456", T2).url("https://blog2.sounie.nz/a/").title("Copy"))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.findById(wpId("456"))).isEmpty();
      Post original = posts.findById(wpId("123")).orElseThrow();
      assertThat(original.title()).isEqualTo(new Title("Original"));
      assertThat(original.updatedAt()).isEqualTo(T1);
      assertThat(events.events()).isEmpty();
      assertThat(report.skipped())
          .singleElement()
          .satisfies(
              skipped -> {
                assertThat(skipped.sourcePostId()).contains(new SourcePostId("456"));
                assertThat(skipped.reason()).isEqualTo(SkipReason.DUPLICATE_CANONICAL_URL);
              });
    }

    @Test
    @DisplayName("AC-CAT-18: an error mid-sync keeps an oldest-first checkpoint at the last page")
    void error_mid_sync_keeps_oldest_first_checkpoint_at_last_fully_handled_page() {
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1))),
          page(available(wpPost("2", T2))),
          failure("HTTP 503 Service Unavailable"));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).containsExactlyInAnyOrder(wpId("1"), wpId("2"));
      assertThat(publishedPostIds()).containsExactly("sounie-wp:1", "sounie-wp:2");
      assertThat(checkpointOf(SOUNIE_WP_ID)).contains(T2);
      assertThat(report.outcome()).isEqualTo(SyncOutcome.PARTIAL);
      assertThat(report.error()).hasValueSatisfying(error -> assertThat(error).contains("503"));
      assertThat(report.pagesFetched()).isEqualTo(2);

      wordPress.willServe(SOUNIE_WP_ID, page());
      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(wordPress.requests().getLast().changedSince())
          .contains(T2.minus(Duration.ofHours(1)));
    }

    @ParameterizedTest
    @EnumSource(
        value = ChangeOrder.class,
        names = {"NEWEST_FIRST", "UNORDERED"})
    @DisplayName("AC-CAT-19: an error with a newest-first or unordered source keeps the checkpoint")
    void error_with_newest_first_or_unordered_source_leaves_checkpoint_unchanged(
        ChangeOrder order) {
      blogger = new FakeBlogSource(order);
      syncSite = newSyncSite();
      Instant checkpoint = T1;
      givenCheckpoint(ELEGANT_ID, checkpoint, NOW);
      blogger.willServe(
          ELEGANT_ID,
          page(available(elegantPost("5", T5)), available(elegantPost("4", T4))),
          failure("HTTP 503 Service Unavailable"));

      SyncReport failed = syncSite.run(ELEGANT_ID, SyncMode.INCREMENTAL);

      assertThat(failed.outcome()).isEqualTo(SyncOutcome.PARTIAL);
      assertThat(posts.findIdsBySite(ELEGANT_ID)).hasSize(2);
      assertThat(publishedPostIds()).containsExactly("elegant:5", "elegant:4");
      assertThat(checkpointOf(ELEGANT_ID)).contains(checkpoint);

      events.clear();
      blogger.willServe(
          ELEGANT_ID,
          page(available(elegantPost("5", T5)), available(elegantPost("4", T4))),
          page(available(elegantPost("3", T3))));

      SyncReport retried = syncSite.run(ELEGANT_ID, SyncMode.INCREMENTAL);

      assertThat(retried.outcome()).isEqualTo(SyncOutcome.COMPLETED);
      assertThat(events.events()).hasSize(1);
      assertThat(publishedPostIds()).containsExactly("elegant:3");
      assertThat(checkpointOf(ELEGANT_ID)).contains(T5);
    }

    @Test
    @DisplayName("AC-CAT-20: a failure on the first page fails the sync and changes nothing")
    void failure_on_first_page_fails_the_sync_and_changes_nothing() {
      givenCheckpoint(SOUNIE_WP_ID, T1, NOW);
      givenStored(wpPost("1", T1).title("Stored"));
      wordPress.willServe(SOUNIE_WP_ID, failure("HTTP 503 Service Unavailable"));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(report.outcome()).isEqualTo(SyncOutcome.FAILED);
      assertThat(report.error()).hasValueSatisfying(error -> assertThat(error).contains("503"));
      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).containsExactly(wpId("1"));
      assertThat(posts.findById(wpId("1")).orElseThrow().title()).isEqualTo(new Title("Stored"));
      assertThat(checkpointOf(SOUNIE_WP_ID)).contains(T1);
      assertThat(checkpoints.saveHistory(SOUNIE_WP_ID)).isEmpty();
      assertThat(events.events()).isEmpty();
    }
  }

  // --- reconcile and withdrawal -------------------------------------------------------------

  @Nested
  class Reconcile {

    private final Instant previousReconcile = NOW.minus(Duration.ofDays(2));

    private void givenStoredPostsOneTwoThree() {
      givenStored(wpPost("1", T1));
      givenStored(wpPost("2", T1));
      givenStored(wpPost("3", T1));
      givenCheckpoint(SOUNIE_WP_ID, T3, previousReconcile);
    }

    @Test
    @DisplayName("AC-CAT-21: a complete reconcile withdraws posts that are no longer listed")
    void complete_reconcile_withdraws_posts_no_longer_listed() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(
          SOUNIE_WP_ID, page(available(wpPost("1", T1)), available(wpPost("3", T1))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).containsExactlyInAnyOrder(wpId("1"), wpId("3"));
      assertThat(events.events())
          .containsExactly(
              new CatalogPostWithdrawn(
                  "sounie-wp:2", "sounie-wp", "https://blog2.sounie.nz/2/", "NO_LONGER_LISTED"));
      assertThat(lastReconciledAtOf(SOUNIE_WP_ID)).contains(NOW);
      assertThat(report.mode()).isEqualTo(SyncMode.RECONCILE);
      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
      assertThat(report.withdrawn()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-CAT-21: a reconcile ignores the checkpoint and fetches everything")
    void reconcile_fetches_everything_regardless_of_checkpoint() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1)), available(wpPost("2", T1)), available(wpPost("3", T1))));

      syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(wordPress.requests().getFirst().changedSince()).isEmpty();
      assertThat(events.events()).isEmpty();
    }

    @Test
    @DisplayName("AC-CAT-22: a PARTIAL reconcile withdraws nothing")
    void partial_reconcile_withdraws_nothing() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(
          SOUNIE_WP_ID, page(available(wpPost("1", T1))), failure("HTTP 503 Service Unavailable"));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(report.outcome()).isEqualTo(SyncOutcome.PARTIAL);
      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).hasSize(3);
      assertThat(events.eventsOfType(CatalogPostWithdrawn.class)).isEmpty();
      assertThat(lastReconciledAtOf(SOUNIE_WP_ID)).contains(previousReconcile);
    }

    @Test
    @DisplayName("AC-CAT-22: a FAILED reconcile withdraws nothing")
    void failed_reconcile_withdraws_nothing() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(SOUNIE_WP_ID, failure("HTTP 503 Service Unavailable"));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(report.outcome()).isEqualTo(SyncOutcome.FAILED);
      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).hasSize(3);
      assertThat(events.events()).isEmpty();
      assertThat(lastReconciledAtOf(SOUNIE_WP_ID)).contains(previousReconcile);
    }

    @Test
    @DisplayName("AC-CAT-22: a complete reconcile that lists nothing withdraws nothing and warns")
    void empty_reconcile_withdraws_nothing_and_warns() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(SOUNIE_WP_ID, page());

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(report.outcome()).isEqualTo(SyncOutcome.COMPLETED);
      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).hasSize(3);
      assertThat(events.events()).isEmpty();
      assertThat(lastReconciledAtOf(SOUNIE_WP_ID)).contains(previousReconcile);
      assertThat(report.warnings())
          .extracting(SyncWarning::kind)
          .contains(SyncWarning.Kind.EMPTY_LISTING_WITHDRAWALS_SUPPRESSED);
    }

    @Test
    @DisplayName("AC-CAT-23: a post whose entry is malformed is not withdrawn")
    void post_with_a_malformed_entry_is_not_withdrawn() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(
          SOUNIE_WP_ID,
          page(available(wpPost("1", T1)), malformed("2", T1), available(wpPost("3", T1))));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(posts.findById(wpId("2"))).isPresent();
      assertThat(events.eventsOfType(CatalogPostWithdrawn.class)).isEmpty();
      assertThat(report.withdrawn()).isZero();
    }

    @Test
    @DisplayName("AC-CAT-23: a malformed entry without a readable ID suppresses all withdrawals")
    void malformed_entry_without_readable_id_suppresses_all_withdrawals() {
      givenStoredPostsOneTwoThree();
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("1", T1)), malformedWithoutId(T1)));

      SyncReport report = syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);

      assertThat(posts.findIdsBySite(SOUNIE_WP_ID)).hasSize(3);
      assertThat(events.eventsOfType(CatalogPostWithdrawn.class)).isEmpty();
      assertThat(report.warnings())
          .extracting(SyncWarning::kind)
          .contains(SyncWarning.Kind.UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED);
      assertThat(lastReconciledAtOf(SOUNIE_WP_ID))
          .as("not marked reconciled, so the reconcile is retried next run")
          .contains(previousReconcile);
    }

    @Test
    @DisplayName("AC-CAT-24: a withdrawn post that comes back is published again")
    void withdrawn_post_that_comes_back_is_published_again() {
      givenStored(wpPost("1", T1));
      givenStored(wpPost("2", T1));
      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("1", T1))));
      syncSite.run(SOUNIE_WP_ID, SyncMode.RECONCILE);
      assertThat(posts.findById(wpId("2"))).as("withdrawn by the reconcile").isEmpty();
      events.clear();

      wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("2", T2))));
      syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(posts.findById(wpId("2"))).isPresent();
      assertThat(events.events())
          .singleElement()
          .isInstanceOfSatisfying(
              CatalogPostPublished.class,
              published -> assertThat(published.postId()).isEqualTo("sounie-wp:2"));
    }
  }

  // --- concurrency ---------------------------------------------------------------------------

  @Test
  @DisplayName("AC-CAT-27: only one sync per site runs at a time")
  void second_sync_of_the_same_site_is_skipped_while_one_is_in_progress() throws Exception {
    wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("1", T1))));
    FakeBlogSource.Gate gate = wordPress.holdNextFetch();
    CompletableFuture<SyncReport> first =
        CompletableFuture.supplyAsync(() -> syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL));
    try {
      boolean started = false;
      while (!started && !first.isDone()) {
        started = gate.awaitStarted(Duration.ofMillis(50));
      }
      if (!started) {
        first.join(); // surfaces the first sync's failure
      }

      SyncReport second = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

      assertThat(second.outcome()).isEqualTo(SyncOutcome.SKIPPED);
      assertThat(second.published()).isZero();
      assertThat(wordPress.requests()).hasSize(1);
      assertThat(events.events()).isEmpty();
    } finally {
      gate.release();
    }
    assertThat(first.get(5, TimeUnit.SECONDS).outcome()).isEqualTo(SyncOutcome.COMPLETED);
    assertThat(events.events()).hasSize(1);
  }

  @Test
  void sync_of_a_site_can_run_again_after_the_previous_one_finished() {
    wordPress.willServe(SOUNIE_WP_ID, page(available(wpPost("1", T1))));

    syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);
    SyncReport second = syncSite.run(SOUNIE_WP_ID, SyncMode.INCREMENTAL);

    assertThat(second.outcome()).isEqualTo(SyncOutcome.COMPLETED);
  }
}
