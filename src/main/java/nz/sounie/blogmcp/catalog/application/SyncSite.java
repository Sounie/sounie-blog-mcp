package nz.sounie.blogmcp.catalog.application;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.Platform;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.SiteId;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpointRepository;
import nz.sounie.blogmcp.shared.event.IntegrationEventPublisher;

/**
 * Brings one site's posts up to date with its blog source. Handles one post per transaction,
 * publishes integration events after each save, and advances the checkpoint according to the
 * source's change order. At most one sync runs per site at a time.
 */
public final class SyncSite {

  private final SiteDirectory siteDirectory;
  private final Map<Platform, BlogSource> sources;
  private final PostRepository posts;
  private final SyncCheckpointRepository checkpoints;
  private final IntegrationEventPublisher events;
  private final Clock clock;
  private final Set<SiteId> sitesBeingSynced = ConcurrentHashMap.newKeySet();

  public SyncSite(
      SiteDirectory siteDirectory,
      Map<Platform, BlogSource> sources,
      PostRepository posts,
      SyncCheckpointRepository checkpoints,
      IntegrationEventPublisher events,
      Clock clock) {
    this.siteDirectory = siteDirectory;
    this.sources = Map.copyOf(sources);
    this.posts = posts;
    this.checkpoints = checkpoints;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @throws UnknownSite if the site is not configured
   */
  public SyncReport run(SiteId siteId, SyncMode mode) {
    Site site = siteDirectory.load().find(siteId).orElseThrow(() -> new UnknownSite(siteId));
    return run(site, mode);
  }

  /** Syncs a site taken from an already loaded sites configuration. */
  SyncReport run(Site site, SyncMode mode) {
    if (!sitesBeingSynced.add(site.id())) {
      return SyncReport.skipped(site.id(), mode);
    }
    try {
      return new SyncRun(site, mode, sourceFor(site), posts, checkpoints, events, clock).run();
    } finally {
      sitesBeingSynced.remove(site.id());
    }
  }

  private BlogSource sourceFor(Site site) {
    BlogSource source = sources.get(site.platform());
    if (source == null) {
      throw new IllegalStateException("No blog source for platform " + site.platform());
    }
    return source;
  }
}
