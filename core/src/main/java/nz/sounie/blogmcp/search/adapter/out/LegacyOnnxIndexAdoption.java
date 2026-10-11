package nz.sounie.blogmcp.search.adapter.out;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Atomically moves files from the former default ONNX index path, {@code search/index/<site>/},
 * into its profile subtree under {@code search/indexes/}.
 */
final class LegacyOnnxIndexAdoption {

  private static final String ONNX_PROFILE = "onnx-bge-small-en-v1.5-q-v1";

  private LegacyOnnxIndexAdoption() {}

  static void adopt(Path dataDirectory, String profileKey, PrintStream errors) {
    if (!ONNX_PROFILE.equals(profileKey)) {
      return;
    }
    Path legacyRoot = dataDirectory.resolve("search").resolve("index");
    if (!Files.isDirectory(legacyRoot)) {
      return;
    }
    Path profileRoot = FileVectorIndex.profileRoot(dataDirectory).resolve(ONNX_PROFILE);
    try (Stream<Path> sites = Files.list(legacyRoot)) {
      sites.filter(Files::isDirectory).forEach(site -> adoptSite(site, profileRoot, errors));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot inspect legacy ONNX index files", e);
    }
  }

  private static void adoptSite(Path site, Path profileRoot, PrintStream errors) {
    try (Stream<Path> files = Files.list(site)) {
      files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".json"))
          .forEach(file -> move(file, profileRoot, errors));
    } catch (IOException e) {
      errors.println("Could not adopt legacy ONNX index directory " + site + ": " + e.getMessage());
      return;
    }
    try (Stream<Path> remaining = Files.list(site)) {
      if (remaining.findAny().isEmpty()) {
        Files.deleteIfExists(site);
      }
    } catch (IOException e) {
      errors.println(
          "Could not remove empty legacy ONNX directory " + site + ": " + e.getMessage());
    }
  }

  private static void move(Path source, Path profileRoot, PrintStream errors) {
    Path target =
        profileRoot.resolve(source.getParent().getFileName()).resolve(source.getFileName());
    try {
      Files.createDirectories(target.getParent());
      if (Files.exists(target)) {
        errors.println(
            "Kept legacy ONNX index file because its profile target already exists: " + source);
        return;
      }
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.FileAlreadyExistsException exists) {
      errors.println(
          "Kept legacy ONNX index file because its profile target already exists: " + source);
    } catch (AtomicMoveNotSupportedException unsupported) {
      errors.println(
          "Could not atomically adopt legacy ONNX index file " + source + ": " + unsupported);
    } catch (IOException e) {
      errors.println("Could not adopt legacy ONNX index file " + source + ": " + e.getMessage());
    }
  }
}
