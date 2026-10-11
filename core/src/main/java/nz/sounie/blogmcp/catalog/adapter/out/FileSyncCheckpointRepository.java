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

  private final Path directory;
  private final JsonFiles<CheckpointFile> files;
  private final ConcurrentMap<SiteId, CheckpointFile> checkpoints;
  private final Object writeLock = new Object();

  private FileSyncCheckpointRepository(
      Path directory,
      JsonFiles<CheckpointFile> files,
      ConcurrentMap<SiteId, CheckpointFile> checkpoints) {
    this.directory = directory;
    this.files = files;
    this.checkpoints = checkpoints;
  }

  /**
   * Loads every stored checkpoint under the data directory, deleting leftover temporary files and
   * quarantining unreadable ones (logged to {@code errors}).
   */
  public static FileSyncCheckpointRepository open(Path dataDirectory, PrintStream errors) {
    Path directory = dataDirectory.resolve("catalog").resolve("checkpoints");
    JsonFiles<CheckpointFile> files =
        new JsonFiles<>(
            directory,
            CheckpointFile.class,
            CheckpointFile::validated,
            file -> fileOf(directory, file.site()),
            "is treated as absent, so the site gets a full fetch and a reconcile");
    ConcurrentMap<SiteId, CheckpointFile> checkpoints =
        files.loadAll(errors).readable().stream()
            .collect(
                Collectors.toConcurrentMap(
                    CheckpointFile::site, Function.identity(), JsonFiles.unreachableDuplicate()));
    return new FileSyncCheckpointRepository(directory, files, checkpoints);
  }

  @Override
  public Optional<SyncCheckpoint> find(SiteId siteId) {
    return Optional.ofNullable(checkpoints.get(siteId)).map(CheckpointFile::toCheckpoint);
  }

  /** Writes the file first, then replaces the stored snapshot. Saves and deletes are serialised. */
  @Override
  public void save(SyncCheckpoint checkpoint) {
    CheckpointFile file = CheckpointFile.of(checkpoint);
    synchronized (writeLock) {
      files.write(file);
      checkpoints.put(checkpoint.siteId(), file);
    }
  }

  @Override
  public void delete(SiteId siteId) {
    synchronized (writeLock) {
      AtomicFile.delete(fileOf(directory, siteId));
      checkpoints.remove(siteId);
    }
  }

  private static Path fileOf(Path directory, SiteId siteId) {
    return directory.resolve(siteId.value() + ".json");
  }
}
