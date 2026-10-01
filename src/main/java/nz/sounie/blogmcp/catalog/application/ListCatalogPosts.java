package nz.sounie.blogmcp.catalog.application;

import java.util.List;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import nz.sounie.blogmcp.shared.query.CatalogPosts;

/** Serves the catalog's current posts through the shared query contract (AC-SRCH-31). */
public final class ListCatalogPosts implements CatalogPosts {

  private final PostRepository posts;

  public ListCatalogPosts(PostRepository posts) {
    this.posts = posts;
  }

  @Override
  public List<CatalogPostState> currentPosts() {
    throw new UnsupportedOperationException("not implemented: " + posts);
  }
}
