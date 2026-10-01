package nz.sounie.blogmcp.catalog.domain;

import java.util.Optional;

/** Port: stored sync checkpoints, one per site. */
public interface SyncCheckpointRepository {

  Optional<SyncCheckpoint> find(SiteId siteId);

  void save(SyncCheckpoint checkpoint);

  void delete(SiteId siteId);
}
