package nz.sounie.blogmcp.catalog.domain.sync;

import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;

/** Port: stored sync checkpoints, one per site. */
public interface SyncCheckpointRepository {

  Optional<SyncCheckpoint> find(SiteId siteId);

  void save(SyncCheckpoint checkpoint);

  void delete(SiteId siteId);
}
