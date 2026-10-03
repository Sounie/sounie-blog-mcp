package nz.sounie.blogmcp.search.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.index.IndexRecipe;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.shared.storage.AtomicFile;
import nz.sounie.blogmcp.shared.storage.FileKey;
import nz.sounie.blogmcp.shared.storage.StoredFiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vector index on disk: one JSON file per post at {@code
 * <data>/search/index/<siteId>/<FileKey(postId external form)>.json}, whose vectors are base64
 * little-endian float32 ({@link VectorCodec}). Every file is loaded when opened; then the index is
 * write-through, with lookups served from memory. {@link IndexedPost} is immutable, so entries are
 * shared as they are.
 *
 * <p>On open, an unreadable file is quarantined and logged; a file built with a different model is
 * deleted and logged ({@link IndexFileLoader}). The next reconcile re-adds both.
 */
public final class FileVectorIndex implements VectorIndex {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String NEXT_STEP = "will be re-indexed by the next reconcile";

  private final Path directory;
  private final String modelId;
  private final IndexRecipe recipe;
  private final ConcurrentMap<PostId, IndexedPost> posts = new ConcurrentHashMap<>();

  private FileVectorIndex(Path directory, String modelId, IndexRecipe recipe) {
    this.directory = directory;
    this.modelId = modelId;
    this.recipe = recipe;
  }

  /**
   * Loads every compatible index file under the data directory.
   *
   * @param modelId the current model ID ({@code PassageEmbedder.modelId()}); written to each file
   *     and compared on load
   * @param recipe the current index recipe, written to each file
   */
  public static FileVectorIndex open(
      Path dataDirectory, String modelId, IndexRecipe recipe, PrintStream errors) {
    FileVectorIndex index =
        new FileVectorIndex(dataDirectory.resolve("search").resolve("index"), modelId, recipe);
    IndexFileLoader loader = new IndexFileLoader(modelId);
    StoredFiles.jsonFilesAfterSweep(index.directory)
        .forEach(file -> index.accept(file, loader.read(StoredFiles.read(file)), errors));
    return index;
  }

  private void accept(Path file, IndexFileLoad load, PrintStream errors) {
    switch (load) {
      case IndexFileLoad.Loaded loaded -> posts.put(loaded.post().id(), loaded.post());
      case IndexFileLoad.IncompatibleModel other -> dropIncompatible(file, other, errors);
      case IndexFileLoad.Unreadable unreadable ->
          AtomicFile.quarantine(file, unreadable.reason(), NEXT_STEP, errors);
    }
  }

  private void dropIncompatible(
      Path file, IndexFileLoad.IncompatibleModel other, PrintStream errors) {
    AtomicFile.delete(file);
    errors.println(
        "Index file "
            + file
            + " was built with model "
            + other.storedModelId()
            + ", not the current model "
            + modelId
            + "; it was deleted and "
            + NEXT_STEP
            + ".");
  }

  @Override
  public Optional<IndexedPost> find(PostId id) {
    return Optional.ofNullable(posts.get(id));
  }

  /** Writes the file first, then replaces the entry. Saves and removals are serialised. */
  @Override
  public synchronized void save(IndexedPost post) {
    AtomicFile.write(
        fileOf(post.id()), JSON.writeValueAsBytes(IndexFile.of(post, modelId, recipe)));
    posts.put(post.id(), post);
  }

  @Override
  public synchronized boolean remove(PostId id) {
    AtomicFile.delete(fileOf(id));
    return posts.remove(id) != null;
  }

  @Override
  public Stream<IndexedPost> all() {
    return List.copyOf(posts.values()).stream();
  }

  @Override
  public Set<PostId> ids() {
    return Set.copyOf(posts.keySet());
  }

  private Path fileOf(PostId id) {
    return directory
        .resolve(id.siteId().value())
        .resolve(FileKey.of(id.external()).value() + ".json");
  }
}
