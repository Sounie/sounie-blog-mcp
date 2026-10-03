package nz.sounie.blogmcp.search.adapter.out;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.shared.storage.StoredFiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads one index file ({@code IndexFile}, Jackson 3) through {@code IndexedPost.restore}, {@code
 * IndexedChunk} and {@code Embedding}, and decides model compatibility: the one place that compares
 * the stored model ID with the current one.
 */
final class IndexFileLoader {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** One check on a parsed file, in order: the first that finds something decides the load. */
  @FunctionalInterface
  private interface Rule {
    Optional<IndexFileLoad> check(IndexFile file);
  }

  private final List<Rule> rules;

  IndexFileLoader(String currentModelId) {
    this.rules =
        List.of(
            IndexFileLoader::knownFormat, IndexFileLoader::namedModel, builtWith(currentModelId));
  }

  /** Never throws: anything wrong with the content is {@link IndexFileLoad.Unreadable}. */
  IndexFileLoad read(byte[] content) {
    return load(() -> content, rules);
  }

  /**
   * Reads a stored file. Never throws: a file that cannot be read from disk, or whose content
   * belongs at another location than {@code locationOf} its post ID, is {@link
   * IndexFileLoad.Unreadable} too.
   */
  IndexFileLoad read(Path file, Function<PostId, Path> locationOf) {
    List<Rule> withLocation =
        Stream.concat(Stream.of(locatedAt(file, locationOf)), rules.stream()).toList();
    return load(() -> StoredFiles.read(file), withLocation);
  }

  private static IndexFileLoad load(Supplier<byte[]> content, List<Rule> rules) {
    try {
      return judge(JSON.readValue(content.get(), IndexFile.class), rules);
    } catch (RuntimeException unreadable) {
      return new IndexFileLoad.Unreadable(StoredFiles.reasonFor(unreadable));
    }
  }

  private static IndexFileLoad judge(IndexFile file, List<Rule> rules) {
    return rules.stream()
        .map(rule -> rule.check(file))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseGet(() -> new IndexFileLoad.Loaded(file.toIndexedPost()));
  }

  /** Two files can never claim the same post ID. */
  private static Rule locatedAt(Path file, Function<PostId, Path> locationOf) {
    return content ->
        Optional.of(locationOf.apply(PostId.parse(content.postId())))
            .filter(expected -> !expected.equals(file))
            .map(expected -> new IndexFileLoad.Unreadable("Its content belongs at " + expected));
  }

  private static Optional<IndexFileLoad> knownFormat(IndexFile file) {
    return file.format() == IndexFile.CURRENT_FORMAT
        ? Optional.empty()
        : Optional.of(new IndexFileLoad.Unreadable("Unknown format " + file.format()));
  }

  /** A file without a model ID is damaged, not built with another model. */
  private static Optional<IndexFileLoad> namedModel(IndexFile file) {
    return file.modelId() == null
        ? Optional.of(new IndexFileLoad.Unreadable("No model ID"))
        : Optional.empty();
  }

  private static Rule builtWith(String currentModelId) {
    return file ->
        currentModelId.equals(file.modelId())
            ? Optional.empty()
            : Optional.of(new IndexFileLoad.IncompatibleModel(file.modelId()));
  }
}
