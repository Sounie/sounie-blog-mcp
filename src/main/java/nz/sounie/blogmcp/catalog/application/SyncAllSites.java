package nz.sounie.blogmcp.catalog.application;

import java.time.Clock;
import java.util.List;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.catalog.domain.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpointRepository;
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
  private final IntegrationEventPublisher events;
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
    this.events = events;
    this.clock = clock;
  }

  /**
   * @param mode the requested mode; {@code INCREMENTAL} is upgraded per site when a reconcile is
   *     due
   * @return one report per configured site, in configuration order
   */
  public List<SyncReport> run(SyncMode mode) {
    throw new UnsupportedOperationException("not implemented");
  }
}
