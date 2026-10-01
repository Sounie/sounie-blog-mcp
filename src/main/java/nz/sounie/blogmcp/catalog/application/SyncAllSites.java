package nz.sounie.blogmcp.catalog.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.SiteId;
import nz.sounie.blogmcp.catalog.domain.SitesConfiguration;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpointRepository;
import nz.sounie.blogmcp.catalog.domain.WithdrawalReason;
import nz.sounie.blogmcp.shared.event.IntegrationEventPublisher;

/**
 * Syncs every configured site independently. Withdraws the posts of sites no longer configured
 * (reason {@code SITE_REMOVED}) and deletes their checkpoints first. Upgrades a site to {@link
 * SyncMode#RECONCILE} when its reconcile is due.
 */
public final class SyncAllSites {

  private final SiteDirectory siteDirectory;
  private final SyncSite syncSite;
  private final PostRepository posts;
  private final SyncCheckpointRepository checkpoints;
  private final PostWithdrawals withdrawals;
  private final Clock clock;

  public SyncAllSites(
      SiteDirectory siteDirectory,
      SyncSite syncSite,
      PostRepository posts,
      SyncCheckpointRepository checkpoints,
      IntegrationEventPublisher events,
      Clock clock) {
    this.siteDirectory = siteDirectory;
    this.syncSite = syncSite;
    this.posts = posts;
    this.checkpoints = checkpoints;
    this.withdrawals = new PostWithdrawals(posts, events);
    this.clock = clock;
  }

  /**
   * @param mode the requested mode; {@code INCREMENTAL} is upgraded per site when a reconcile is
   *     due
   * @return one report per configured site, in configuration order
   */
  public List<SyncReport> run(SyncMode mode) {
    SitesConfiguration configuration = siteDirectory.load();
    withdrawRemovedSites(configuration.siteIds());
    return configuration.sites().stream().map(site -> syncIndependently(site, mode)).toList();
  }

  private void withdrawRemovedSites(Set<SiteId> configured) {
    posts.findSiteIds().stream()
        .filter(siteId -> !configured.contains(siteId))
        .forEach(this::withdrawRemovedSite);
  }

  /** AC-CAT-32: every post of a site no longer configured is withdrawn, and its checkpoint goes. */
  private void withdrawRemovedSite(SiteId siteId) {
    posts.findIdsBySite(siteId).stream()
        .map(posts::findById)
        .flatMap(Optional::stream)
        .forEach(post -> withdrawals.withdraw(post, WithdrawalReason.SITE_REMOVED));
    checkpoints.delete(siteId);
  }

  /** A failure of one site becomes its report, so the other sites still sync (AC-CAT-26). */
  private SyncReport syncIndependently(Site site, SyncMode requested) {
    SyncMode mode = modeFor(site, requested);
    try {
      return syncSite.run(site, mode);
    } catch (RuntimeException e) {
      return SyncReport.failed(site.id(), mode, String.valueOf(e.getMessage()));
    }
  }

  private SyncMode modeFor(Site site, SyncMode requested) {
    boolean reconcileDue =
        checkpoints
            .find(site.id())
            .map(checkpoint -> checkpoint.isReconcileDue(clock.instant()))
            .orElse(true);
    return requested == SyncMode.RECONCILE || reconcileDue
        ? SyncMode.RECONCILE
        : SyncMode.INCREMENTAL;
  }
}
