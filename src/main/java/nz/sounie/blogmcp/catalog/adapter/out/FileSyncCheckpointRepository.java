package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;
import nz.sounie.blogmcp.shared.storage.AtomicFile;

/**
 * Stored sync checkpoints, one JSON file per site at {@code
 * <data>/catalog/checkpoints/<siteId>.json} (Jackson 3). Loaded when opened, then write-through;
 * every find restores a fresh {@link SyncCheckpoint}. An unreadable file is quarantined, logged and
 * treated as absent, which already means a full fetch and a due reconcile for that site.
 */
public final class FileSyncCheckpointRepository implements SyncCheckpointRepository {

  private static final JsonFiles<CheckpointFile> FILES =
      new JsonFiles<>(
          CheckpointFile.class,
          CheckpointFile::validated,
          "is treated as absent, so the site gets a full fetch and a reconcile");

  private final Path directory;
  private final ConcurrentMap<SiteId, CheckpointFile> checkpoints;

  private FileSyncCheckpointRepository(
      Path directory, ConcurrentMap<SiteId, CheckpointFile> checkpoints) {
    this.directory = directory;
    this.checkpoints = checkpoints;
  }

  /**
   * Loads every stored checkpoint under the data directory, deleting leftover temporary files and
   * quarantining unreadable ones (logged to {@code errors}).
   */
  public static FileSyncCheckpointRepository open(Path dataDirectory, PrintStream errors) {
    Path directory = dataDirectory.resolve("catalog").resolve("checkpoints");
    ConcurrentMap<SiteId, CheckpointFile> checkpoints =
        FILES.loadAll(directory, errors).readable().stream()
            .collect(
                Collectors.toConcurrentMap(
                    CheckpointFile::site, Function.identity(), (first, second) -> second));
    return new FileSyncCheckpointRepository(directory, checkpoints);
  }

  @Override
  public Optional<SyncCheckpoint> find(SiteId siteId) {
    return Optional.ofNullable(checkpoints.get(siteId)).map(CheckpointFile::toCheckpoint);
  }

  /** Writes the file first, then replaces the stored snapshot. Saves and deletes are serialised. */
  @Override
  public synchronized void save(SyncCheckpoint checkpoint) {
    CheckpointFile file = CheckpointFile.of(checkpoint);
    FILES.write(fileOf(checkpoint.siteId()), file);
    checkpoints.put(checkpoint.siteId(), file);
  }

  @Override
  public synchronized void delete(SiteId siteId) {
    AtomicFile.delete(fileOf(siteId));
    checkpoints.remove(siteId);
  }

  private Path fileOf(SiteId siteId) {
    return directory.resolve(siteId.value() + ".json");
  }
}
