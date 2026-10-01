package nz.sounie.blogmcp.catalog.domain;

import java.util.Optional;
import java.util.Set;

/** Port: stored posts. */
public interface PostRepository {

  Optional<Post> findById(PostId id);

  /** Finds the post whose canonical URL has the same {@link CanonicalUrl#normalisedForm()}. */
  Optional<Post> findByCanonicalUrl(CanonicalUrl url);

  Set<PostId> findIdsBySite(SiteId siteId);

  /** Every site ID that has at least one stored post. */
  Set<SiteId> findSiteIds();

  void save(Post post);

  void delete(PostId id);
}
