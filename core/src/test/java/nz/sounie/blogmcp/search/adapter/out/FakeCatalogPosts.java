package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import nz.sounie.blogmcp.shared.query.CatalogPosts;

/** Fake of our shared {@link CatalogPosts} query contract. */
public final class FakeCatalogPosts implements CatalogPosts {

  private final List<CatalogPostState> states;

  public FakeCatalogPosts(CatalogPostState... states) {
    this.states = List.of(states);
  }

  @Override
  public List<CatalogPostState> currentPosts() {
    return states;
  }
}
