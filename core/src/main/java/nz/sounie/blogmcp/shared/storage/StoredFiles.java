package nz.sounie.blogmcp.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Finding and reading the stored files under a repository's directory, for loading at startup. */
public final class StoredFiles {

  private static final String STORED_SUFFIX = ".json";

  private StoredFiles() {}

  /**
   * Sweeps leftover temporary files ({@link AtomicFile#sweep}), then lists every {@code *.json}
   * file under the directory, at any depth, in path order. A missing directory holds none.
   *
   * @throws java.io.UncheckedIOException if the directory cannot be walked
   */
  public static List<Path> jsonFilesAfterSweep(Path directory) {
    AtomicFile.sweep(directory);
    return filesUnder(directory).stream()
        .filter(file -> file.getFileName().toString().endsWith(STORED_SUFFIX))
        .sorted()
        .toList();
  }

  /**
   * The whole content of a file.
   *
   * @throws java.io.UncheckedIOException if it cannot be read
   */
  public static byte[] read(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + file, e);
    }
  }

  /** Why a stored file could not be turned back into its value, for the quarantine log line. */
  public static String reasonFor(RuntimeException problem) {
    return problem.getClass().getSimpleName() + ": " + problem.getMessage();
  }

  /** Every regular file under the directory, at any depth; none if the directory is missing. */
  static List<Path> filesUnder(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths.filter(Files::isRegularFile).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not walk " + directory, e);
    }
  }
}
