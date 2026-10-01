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
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.adapter.out.FakeBlogSource;
import nz.sounie.blogmcp.catalog.adapter.out.FixedSiteDirectory;
import nz.sounie.blogmcp.catalog.adapter.out.InMemoryPostRepository;
import nz.sounie.blogmcp.catalog.adapter.out.InMemorySyncCheckpointRepository;
import nz.sounie.blogmcp.catalog.adapter.out.RecordingEventPublisher;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.InvalidSitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.Platform;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.SiteId;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationMissing;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpoint;
import nz.sounie.blogmcp.shared.event.CatalogPostWithdrawn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncAllSitesTest {

  private static final Instant NOW = Instant.parse("2026-10-01T06:00:00Z");
  private static final Instant T1 = Instant.parse("2026-09-20T01:00:00Z");
  private static final SiteId OLD_BLOG_ID = new SiteId("old-blog");
  private static final Site OLD_BLOG =
      new Site(OLD_BLOG_ID, Platform.WORDPRESS, URI.create("https://old.example"));

  private final InMemoryPostRepository posts = new InMemoryPostRepository();
  private final InMemorySyncCheckpointRepository checkpoints =
      new InMemorySyncCheckpointRepository();
  private final RecordingEventPublisher events = new RecordingEventPublisher();
  private final FakeBlogSource wordPress = new FakeBlogSource(ChangeOrder.OLDEST_FIRST);
  private final FakeBlogSource blogger = new FakeBlogSource(ChangeOrder.NEWEST_FIRST);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private SyncAllSites syncAllSitesWith(SiteDirectory directory) {
    SyncSite syncSite =
        new SyncSite(
            directory,
            Map.of(Platform.WORDPRESS, wordPress, Platform.BLOGGER, blogger),
            posts,
            checkpoints,
            events,
            clock);
    return new SyncAllSites(directory, syncSite, posts, checkpoints, events, clock);
  }

  private SyncAllSites syncAllSites() {
    return syncAllSitesWith(FixedSiteDirectory.of(SOUNIE_WP_DEFINITION, ELEGANT_DEFINITION));
  }

  private void givenReconciledAt(SiteId siteId, Instant lastReconciledAt) {
    checkpoints.given(
        SyncCheckpoint.restore(siteId, Optional.of(T1), Optional.ofNullable(lastReconciledAt)));
  }

  private static SourceEntry available(Site site, String sourcePostId) {
    return new SourceEntry.Available(
        aSnapshot().on(site).sourcePostId(sourcePostId).updatedAt(T1).build());
  }

  @Test
  @DisplayName("AC-CAT-25: an invalid sites configuration starts no sync")
  void invalid_configuration_starts_no_sync() {
    InvalidSitesConfiguration invalid =
        new InvalidSitesConfiguration(
            List.of(
                new SitesConfigurationViolation(
                    SitesConfigurationViolation.Kind.DUPLICATE_SITE_ID, "blog")));
    SyncAllSites syncAllSites = syncAllSitesWith(FixedSiteDirectory.failingWith(invalid));

    assertThatThrownBy(() -> syncAllSites.run(SyncMode.INCREMENTAL)).isSameAs(invalid);
    assertThat(wordPress.requests()).isEmpty();
    assertThat(blogger.requests()).isEmpty();
  }

  @Test
  @DisplayName("AC-CAT-25: a missing sites configuration starts no sync")
  void missing_configuration_starts_no_sync() {
    SitesConfigurationMissing missing =
        new SitesConfigurationMissing(Path.of("/home/owner/.config/blog-mcp/sites.json"));
    SyncAllSites syncAllSites = syncAllSitesWith(FixedSiteDirectory.failingWith(missing));

    assertThatThrownBy(() -> syncAllSites.run(SyncMode.INCREMENTAL)).isSameAs(missing);
    assertThat(wordPress.requests()).isEmpty();
    assertThat(blogger.requests()).isEmpty();
  }

  @Test
  @DisplayName("AC-CAT-26: sites sync independently")
  void a_failing_site_does_not_stop_the_others() {
    givenReconciledAt(SOUNIE_WP_ID, NOW);
    givenReconciledAt(ELEGANT_ID, NOW);
    wordPress.willServe(SOUNIE_WP_ID, failure("HTTP 503 Service Unavailable"));
    blogger.willServe(ELEGANT_ID, page(available(ELEGANT, "1")));

    List<SyncReport> reports = syncAllSites().run(SyncMode.INCREMENTAL);

    assertThat(reports)
        .extracting(SyncReport::siteId, SyncReport::outcome)
        .containsExactly(
            tuple(SOUNIE_WP_ID, SyncOutcome.FAILED), tuple(ELEGANT_ID, SyncOutcome.COMPLETED));
    assertThat(posts.findIdsBySite(ELEGANT_ID))
        .containsExactly(new PostId(ELEGANT_ID, new SourcePostId("1")));
  }

  @Test
  @DisplayName("AC-CAT-28: a site whose reconcile is due is reconciled; others sync incrementally")
  void reconciles_sites_whose_reconcile_is_due() {
    givenReconciledAt(SOUNIE_WP_ID, NOW.minus(Duration.ofHours(25)));
    givenReconciledAt(ELEGANT_ID, NOW.minus(Duration.ofHours(2)));

    List<SyncReport> reports = syncAllSites().run(SyncMode.INCREMENTAL);

    assertThat(reports)
        .extracting(SyncReport::siteId, SyncReport::mode)
        .containsExactly(
            tuple(SOUNIE_WP_ID, SyncMode.RECONCILE), tuple(ELEGANT_ID, SyncMode.INCREMENTAL));
    assertThat(wordPress.requests().getFirst().changedSince()).isEmpty();
    assertThat(blogger.requests().getFirst().changedSince()).isPresent();
  }

  @Test
  @DisplayName("AC-CAT-28: a site that has never been reconciled is reconciled")
  void reconciles_a_site_that_was_never_reconciled() {
    givenReconciledAt(ELEGANT_ID, NOW.minus(Duration.ofHours(2)));

    List<SyncReport> reports = syncAllSites().run(SyncMode.INCREMENTAL);

    assertThat(reports)
        .extracting(SyncReport::siteId, SyncReport::mode)
        .containsExactly(
            tuple(SOUNIE_WP_ID, SyncMode.RECONCILE), tuple(ELEGANT_ID, SyncMode.INCREMENTAL));
  }

  @Test
  void a_requested_reconcile_reconciles_every_site() {
    givenReconciledAt(SOUNIE_WP_ID, NOW.minus(Duration.ofHours(2)));
    givenReconciledAt(ELEGANT_ID, NOW.minus(Duration.ofHours(2)));

    List<SyncReport> reports = syncAllSites().run(SyncMode.RECONCILE);

    assertThat(reports).extracting(SyncReport::mode).containsOnly(SyncMode.RECONCILE);
  }

  @Test
  @DisplayName("AC-CAT-32: posts of a site removed from the configuration are withdrawn")
  void withdraws_posts_and_deletes_checkpoint_of_a_removed_site() {
    posts.save(aSnapshot().on(OLD_BLOG).sourcePostId("1").buildStoredPost());
    posts.save(aSnapshot().on(OLD_BLOG).sourcePostId("2").buildStoredPost());
    posts.save(aSnapshot().on(ELEGANT).sourcePostId("9").buildStoredPost());
    checkpoints.given(SyncCheckpoint.restore(OLD_BLOG_ID, Optional.of(T1), Optional.of(NOW)));
    givenReconciledAt(SOUNIE_WP_ID, NOW);
    givenReconciledAt(ELEGANT_ID, NOW);

    List<SyncReport> reports = syncAllSites().run(SyncMode.INCREMENTAL);

    assertThat(posts.findIdsBySite(OLD_BLOG_ID)).isEmpty();
    assertThat(events.eventsOfType(CatalogPostWithdrawn.class))
        .containsExactlyInAnyOrder(
            new CatalogPostWithdrawn(
                "old-blog:1", "old-blog", "https://old.example/1/", "SITE_REMOVED"),
            new CatalogPostWithdrawn(
                "old-blog:2", "old-blog", "https://old.example/2/", "SITE_REMOVED"));
    assertThat(checkpoints.find(OLD_BLOG_ID)).isEmpty();
    assertThat(posts.findIdsBySite(ELEGANT_ID)).hasSize(1);
    assertThat(reports).extracting(SyncReport::siteId).containsExactly(SOUNIE_WP_ID, ELEGANT_ID);
  }
}
