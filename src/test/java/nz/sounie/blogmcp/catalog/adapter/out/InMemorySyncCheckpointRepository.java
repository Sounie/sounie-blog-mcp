package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;

/**
 * In-memory fake of {@link SyncCheckpointRepository}. Stores copies and records every save, so
 * tests can see when the checkpoint was saved and with which value.
 */
public final class InMemorySyncCheckpointRepository implements SyncCheckpointRepository {

  private final Map<SiteId, SyncCheckpoint> checkpoints = new HashMap<>();
  private final List<SyncCheckpoint> saveHistory = new ArrayList<>();

  @Override
  public synchronized Optional<SyncCheckpoint> find(SiteId siteId) {
    return Optional.ofNullable(checkpoints.get(siteId)).map(InMemorySyncCheckpointRepository::copy);
  }

  @Override
  public synchronized void save(SyncCheckpoint checkpoint) {
    checkpoints.put(checkpoint.siteId(), copy(checkpoint));
    saveHistory.add(copy(checkpoint));
  }

  @Override
  public synchronized void delete(SiteId siteId) {
    checkpoints.remove(siteId);
  }

  /** Stores a checkpoint as test setup, without recording it as a save. */
  public synchronized void given(SyncCheckpoint checkpoint) {
    checkpoints.put(checkpoint.siteId(), copy(checkpoint));
  }

  /** Copies of every checkpoint saved, in order, for one site. */
  public synchronized List<SyncCheckpoint> saveHistory(SiteId siteId) {
    return saveHistory.stream().filter(c -> c.siteId().equals(siteId)).toList();
  }

  private static SyncCheckpoint copy(SyncCheckpoint checkpoint) {
    return SyncCheckpoint.restore(
        checkpoint.siteId(), checkpoint.changesSeenUpTo(), checkpoint.lastReconciledAt());
  }
}
