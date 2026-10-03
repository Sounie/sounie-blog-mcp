package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import java.util.Optional;
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
    this.rules = List.of(IndexFileLoader::knownFormat, builtWith(currentModelId));
  }

  /** Never throws: anything wrong with the content is {@link IndexFileLoad.Unreadable}. */
  IndexFileLoad read(byte[] content) {
    try {
      return judge(JSON.readValue(content, IndexFile.class));
    } catch (RuntimeException unreadable) {
      return new IndexFileLoad.Unreadable(StoredFiles.reasonFor(unreadable));
    }
  }

  private IndexFileLoad judge(IndexFile file) {
    return rules.stream()
        .map(rule -> rule.check(file))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseGet(() -> new IndexFileLoad.Loaded(file.toIndexedPost()));
  }

  private static Optional<IndexFileLoad> knownFormat(IndexFile file) {
    return file.format() == IndexFile.CURRENT_FORMAT
        ? Optional.empty()
        : Optional.of(new IndexFileLoad.Unreadable("Unknown format " + file.format()));
  }

  private static Rule builtWith(String currentModelId) {
    return file ->
        currentModelId.equals(file.modelId())
            ? Optional.empty()
            : Optional.of(new IndexFileLoad.IncompatibleModel(file.modelId()));
  }
}
