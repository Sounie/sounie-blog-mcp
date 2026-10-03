package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;

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

  private FilePostRepository() {}

  /**
   * Loads every stored post under the data directory, deleting leftover temporary files and
   * quarantining unreadable ones (logged to {@code errors}).
   */
  public static FilePostRepository open(Path dataDirectory, PrintStream errors) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Whether loading found any unreadable post file. */
  public StorageHealth health() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Optional<Post> findById(PostId id) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Optional<Post> findByCanonicalUrl(CanonicalUrl url) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Set<PostId> findIdsBySite(SiteId siteId) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Set<SiteId> findSiteIds() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void save(Post post) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void delete(PostId id) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
