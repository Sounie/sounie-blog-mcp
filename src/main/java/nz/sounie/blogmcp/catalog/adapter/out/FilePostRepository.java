package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.shared.storage.AtomicFile;
import nz.sounie.blogmcp.shared.storage.FileKey;

/**
 * Stored posts, one JSON file per post at {@code
 * <data>/catalog/posts/<siteId>/<FileKey(sourcePostId)>.json} (Jackson 3). Every file is loaded
 * when opened; then the store is write-through, with lookups served from memory.
 *
 * <p>Keeps immutable stored snapshots and restores a fresh {@link Post} on every find, so no two
 * callers ever share a mutable post. An unreadable file is quarantined and logged, and makes the
 * {@link #health()} {@link StorageHealth#DAMAGED}.
 */
public final class FilePostRepository implements PostRepository {

  private final Path directory;
  private final JsonFiles<PostFile> files;
  private final ConcurrentMap<PostId, PostFile> posts;
  private final StorageHealth health;

  private FilePostRepository(
      Path directory,
      JsonFiles<PostFile> files,
      ConcurrentMap<PostId, PostFile> posts,
      StorageHealth health) {
    this.directory = directory;
    this.files = files;
    this.posts = posts;
    this.health = health;
  }

  /**
   * Loads every stored post under the data directory, deleting leftover temporary files and
   * quarantining unreadable ones (logged to {@code errors}).
   */
  public static FilePostRepository open(Path dataDirectory, PrintStream errors) {
    Path directory = dataDirectory.resolve("catalog").resolve("posts");
    JsonFiles<PostFile> files =
        new JsonFiles<>(
            directory,
            PostFile.class,
            PostFile::validated,
            file -> fileOf(directory, file.id()),
            "will be fetched again by a reconcile");
    JsonFiles.Loaded<PostFile> loaded = files.loadAll(errors);
    ConcurrentMap<PostId, PostFile> posts =
        loaded.readable().stream()
            .collect(
                Collectors.toConcurrentMap(
                    PostFile::id, Function.identity(), JsonFiles.unreachableDuplicate()));
    return new FilePostRepository(
        directory, files, posts, StorageHealth.afterQuarantining(loaded.quarantined()));
  }

  /** Whether loading found any unreadable post file. */
  public StorageHealth health() {
    return health;
  }

  @Override
  public Optional<Post> findById(PostId id) {
    return Optional.ofNullable(posts.get(id)).map(PostFile::toPost);
  }

  @Override
  public Optional<Post> findByCanonicalUrl(CanonicalUrl url) {
    String wanted = url.normalisedForm();
    return posts.values().stream()
        .filter(file -> file.url().normalisedForm().equals(wanted))
        .findFirst()
        .map(PostFile::toPost);
  }

  @Override
  public Set<PostId> findIdsBySite(SiteId siteId) {
    return posts.keySet().stream()
        .filter(id -> id.siteId().equals(siteId))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public Set<SiteId> findSiteIds() {
    return posts.keySet().stream().map(PostId::siteId).collect(Collectors.toUnmodifiableSet());
  }

  /** Writes the file first, then replaces the stored snapshot. Saves and deletes are serialised. */
  @Override
  public synchronized void save(Post post) {
    PostFile file = PostFile.of(post);
    files.write(file);
    posts.put(post.id(), file);
  }

  @Override
  public synchronized void delete(PostId id) {
    AtomicFile.delete(fileOf(directory, id));
    posts.remove(id);
  }

  private static Path fileOf(Path directory, PostId id) {
    return directory
        .resolve(id.siteId().value())
        .resolve(FileKey.of(id.sourcePostId().value()).value() + ".json");
  }
}
