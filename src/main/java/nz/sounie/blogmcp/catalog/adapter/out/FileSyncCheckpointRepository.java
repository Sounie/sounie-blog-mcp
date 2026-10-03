package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;

/**
 * Stored sync checkpoints, one JSON file per site at {@code
 * <data>/catalog/checkpoints/<siteId>.json} (Jackson 3). Loaded when opened, then write-through;
 * every find restores a fresh {@link SyncCheckpoint}. An unreadable file is quarantined, logged and
 * treated as absent, which already means a full fetch and a due reconcile for that site.
 */
public final class FileSyncCheckpointRepository implements SyncCheckpointRepository {

  private FileSyncCheckpointRepository() {}

  /**
   * Loads every stored checkpoint under the data directory, deleting leftover temporary files and
   * quarantining unreadable ones (logged to {@code errors}).
   */
  public static FileSyncCheckpointRepository open(Path dataDirectory, PrintStream errors) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Optional<SyncCheckpoint> find(SiteId siteId) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void save(SyncCheckpoint checkpoint) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void delete(SiteId siteId) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
