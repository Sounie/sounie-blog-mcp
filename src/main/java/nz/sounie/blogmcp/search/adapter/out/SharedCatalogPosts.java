package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import nz.sounie.blogmcp.search.application.PostCatalog;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.shared.query.CatalogPosts;

/** Anti-corruption layer: the shared {@link CatalogPosts} query as search's {@link PostCatalog}. */
public final class SharedCatalogPosts implements PostCatalog {

  public SharedCatalogPosts(CatalogPosts catalog) {}

  @Override
  public List<PostToIndex> currentPosts() {
    throw new UnsupportedOperationException("not implemented");
  }
}
