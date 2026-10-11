package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Copies model files bundled in the jar into a new directory, because ONNX Runtime reads a graph's
 * external weights from a file beside it (ADR 0010). Each process gets its own directory, readable
 * only by its user and deleted when the JVM exits, so no other process shares or replaces the
 * files.
 */
final class BundledModelFiles {

  private BundledModelFiles() {}

  /**
   * Extracts {@code resourceDirectory/name} for each name, keeping the names.
   *
   * @throws IllegalStateException if a named resource is not bundled
   * @throws UncheckedIOException if the files cannot be written
   */
  static Path extract(Class<?> anchor, String resourceDirectory, List<String> names) {
    Path directory = newDirectory();
    names.forEach(name -> copy(anchor, resourceDirectory + "/" + name, directory.resolve(name)));
    return directory;
  }

  private static Path newDirectory() {
    try {
      Path directory = Files.createTempDirectory("blog-mcp-model-");
      // Registered first, so deleted last: after the files inside it.
      directory.toFile().deleteOnExit();
      return directory;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create a directory for the bundled model", e);
    }
  }

  private static void copy(Class<?> anchor, String resource, Path target) {
    try (InputStream in = anchor.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing bundled model file " + resource);
      }
      Files.copy(in, target);
      target.toFile().deleteOnExit();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract the bundled model file " + resource, e);
    }
  }
}
