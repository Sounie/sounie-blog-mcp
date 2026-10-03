package nz.sounie.blogmcp.shared.storage;

import java.io.PrintStream;
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

  private AtomicFile() {}

  /**
   * Writes the content to the target atomically, creating missing parent directories.
   *
   * @throws java.io.UncheckedIOException if the write fails; the target is then unchanged
   */
  public static void write(Path target, byte[] content) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /**
   * Deletes the target if it exists.
   *
   * @throws java.io.UncheckedIOException if it exists and cannot be deleted
   */
  public static void delete(Path target) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /**
   * Deletes every {@code *.tmp} file under the directory, at any depth, without reading it. A
   * missing directory has nothing to sweep.
   *
   * @throws java.io.UncheckedIOException if the directory cannot be walked
   */
  public static void sweep(Path directory) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /**
   * Renames an unreadable file to {@code <name>.corrupt}, replacing any earlier quarantined copy,
   * and logs one line naming the path, the reason and what happens next.
   *
   * @throws java.io.UncheckedIOException if the file cannot be renamed
   */
  public static void quarantine(Path file, String reason, String nextStep, PrintStream errors) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
