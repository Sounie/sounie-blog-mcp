package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import nz.sounie.blogmcp.shared.storage.AtomicFile;
import nz.sounie.blogmcp.shared.storage.StoredFiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * The catalog's stored files of one kind ({@link PostFile} or {@link CheckpointFile}), mapped by
 * Jackson 3: loading every file under a directory, quarantining unreadable ones, and atomic writes.
 *
 * @param <F> the file's mapping record
 */
final class JsonFiles<F> {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Class<F> type;
  private final UnaryOperator<F> validated;
  private final String nextStep;

  /**
   * @param validated returns the file if its content restores a valid aggregate, otherwise throws
   * @param nextStep what happens to a quarantined file's aggregate, for the log line
   */
  JsonFiles(Class<F> type, UnaryOperator<F> validated, String nextStep) {
    this.type = type;
    this.validated = validated;
    this.nextStep = nextStep;
  }

  /** The readable files, and how many were quarantined. */
  record Loaded<F>(List<F> readable, int quarantined) {}

  /**
   * Deletes leftover temporary files, then reads every stored file under the directory. An
   * unreadable one is quarantined and logged to {@code errors}.
   */
  Loaded<F> loadAll(Path directory, PrintStream errors) {
    List<Path> files = StoredFiles.jsonFilesAfterSweep(directory);
    List<F> readable =
        files.stream().flatMap(file -> readOrQuarantine(file, errors).stream()).toList();
    return new Loaded<>(readable, files.size() - readable.size());
  }

  private Optional<F> readOrQuarantine(Path file, PrintStream errors) {
    try {
      return Optional.of(validated.apply(JSON.readValue(StoredFiles.read(file), type)));
    } catch (RuntimeException unreadable) {
      AtomicFile.quarantine(file, StoredFiles.reasonFor(unreadable), nextStep, errors);
      return Optional.empty();
    }
  }

  /** Writes the file's JSON atomically. */
  void write(Path target, F file) {
    AtomicFile.write(target, JSON.writeValueAsBytes(file));
  }
}
