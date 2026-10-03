package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import nz.sounie.blogmcp.shared.storage.AtomicFile;
import nz.sounie.blogmcp.shared.storage.StoredFiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * The catalog's stored files of one kind ({@link PostFile} or {@link CheckpointFile}) under one
 * directory, mapped by Jackson 3: loading every file, quarantining unreadable ones, and atomic
 * writes. A file is unreadable if it cannot be read, is not valid, or is not at the location its
 * content maps to.
 *
 * @param <F> the file's mapping record
 */
final class JsonFiles<F> {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path directory;
  private final Class<F> type;
  private final UnaryOperator<F> validated;
  private final Function<F, Path> location;
  private final String nextStep;

  /**
   * @param validated returns the file if its content restores a valid aggregate, otherwise throws
   * @param location where a file with this content belongs
   * @param nextStep what happens to a quarantined file's aggregate, for the log line
   */
  JsonFiles(
      Path directory,
      Class<F> type,
      UnaryOperator<F> validated,
      Function<F, Path> location,
      String nextStep) {
    this.directory = directory;
    this.type = type;
    this.validated = validated;
    this.location = location;
    this.nextStep = nextStep;
  }

  /** The readable files, and how many were quarantined. */
  record Loaded<F>(List<F> readable, int quarantined) {}

  /**
   * Deletes leftover temporary files, then reads every stored file under the directory. An
   * unreadable one is quarantined and logged to {@code errors}.
   */
  Loaded<F> loadAll(PrintStream errors) {
    List<Path> files = StoredFiles.jsonFilesAfterSweep(directory);
    List<F> readable =
        files.stream().flatMap(file -> readOrQuarantine(file, errors).stream()).toList();
    return new Loaded<>(readable, files.size() - readable.size());
  }

  private Optional<F> readOrQuarantine(Path file, PrintStream errors) {
    try {
      return Optional.of(
          locatedAt(file, validated.apply(JSON.readValue(StoredFiles.read(file), type))));
    } catch (RuntimeException unreadable) {
      AtomicFile.quarantine(file, StoredFiles.reasonFor(unreadable), nextStep, errors);
      return Optional.empty();
    }
  }

  /**
   * @throws IllegalArgumentException if the content belongs at another location, so that two files
   *     can never claim the same ID
   */
  private F locatedAt(Path file, F content) {
    Path expected = location.apply(content);
    if (!expected.equals(file)) {
      throw new IllegalArgumentException("Its content belongs at " + expected);
    }
    return content;
  }

  /**
   * A merge for collecting loaded files by ID. Unreachable: every loaded file is at its content's
   * location, so no two files can claim the same ID.
   */
  static <F> BinaryOperator<F> unreachableDuplicate() {
    return (first, second) -> {
      throw new IllegalStateException("Two stored files claim the same ID: " + first);
    };
  }

  /** Writes the content's JSON atomically, at its location. */
  void write(F content) {
    AtomicFile.write(location.apply(content), JSON.writeValueAsBytes(content));
  }
}
