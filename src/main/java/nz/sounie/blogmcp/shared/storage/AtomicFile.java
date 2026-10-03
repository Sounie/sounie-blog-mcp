package nz.sounie.blogmcp.shared.storage;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Atomic file writes, deletes, sweeping of leftover temporary files, and quarantine of unreadable
 * files (ADR 0007).
 *
 * <p>A write goes to {@code <target>.tmp} in the target's directory, is forced to disk, then moved
 * over the target with {@code ATOMIC_MOVE}. A reader sees either the old file or the new one, never
 * a partial one.
 */
public final class AtomicFile {

  private static final String TEMPORARY_SUFFIX = ".tmp";
  private static final String QUARANTINE_SUFFIX = ".corrupt";

  private AtomicFile() {}

  /**
   * Writes the content to the target atomically, creating missing parent directories.
   *
   * @throws java.io.UncheckedIOException if the write fails; the target is then unchanged
   */
  public static void write(Path target, byte[] content) {
    Path temporary = sibling(target, TEMPORARY_SUFFIX);
    try {
      Files.createDirectories(target.toAbsolutePath().getParent());
      writeDurably(temporary, content);
      Files.move(temporary, target, ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write " + target, e);
    }
  }

  private static void writeDurably(Path file, byte[] content) throws IOException {
    try (FileChannel channel = FileChannel.open(file, CREATE, TRUNCATE_EXISTING, WRITE)) {
      ByteBuffer remaining = ByteBuffer.wrap(content);
      while (remaining.hasRemaining()) {
        channel.write(remaining);
      }
      channel.force(true);
    }
  }

  /**
   * Deletes the target if it exists.
   *
   * @throws java.io.UncheckedIOException if it exists and cannot be deleted
   */
  public static void delete(Path target) {
    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not delete " + target, e);
    }
  }

  /**
   * Deletes every {@code *.tmp} file under the directory, at any depth, without reading it. A
   * missing directory has nothing to sweep.
   *
   * @throws java.io.UncheckedIOException if the directory cannot be walked
   */
  public static void sweep(Path directory) {
    StoredFiles.filesUnder(directory).stream()
        .filter(file -> file.getFileName().toString().endsWith(TEMPORARY_SUFFIX))
        .forEach(AtomicFile::delete);
  }

  /**
   * Renames an unreadable file to {@code <name>.corrupt}, replacing any earlier quarantined copy,
   * and logs one line naming the path, the reason and what happens next.
   *
   * @throws java.io.UncheckedIOException if the file cannot be renamed
   */
  public static void quarantine(Path file, String reason, String nextStep, PrintStream errors) {
    Path quarantined = sibling(file, QUARANTINE_SUFFIX);
    try {
      Files.move(file, quarantined, REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not quarantine " + file, e);
    }
    errors.println(
        "Unreadable file "
            + file
            + " ("
            + oneLine(reason)
            + ") was moved to "
            + quarantined.getFileName()
            + "; it "
            + oneLine(nextStep)
            + ".");
  }

  private static Path sibling(Path file, String suffix) {
    return file.resolveSibling(file.getFileName() + suffix);
  }

  private static String oneLine(String text) {
    return text.strip().replaceAll("\\s+", " ");
  }
}
