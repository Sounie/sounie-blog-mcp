package nz.sounie.blogmcp.search.adapter.in;

import nz.sounie.blogmcp.search.domain.IndexChange;
import nz.sounie.blogmcp.shared.event.IntegrationEvent;

/**
 * Anti-corruption layer: catalog integration events to index changes. Publish and revise become
 * {@code Upsert}; withdraw becomes {@code Remove}.
 */
public final class CatalogEventTranslation {

  private CatalogEventTranslation() {}

  /**
   * @throws nz.sounie.blogmcp.search.domain.MalformedCatalogPost for an invalid payload
   */
  public static IndexChange toChange(IntegrationEvent event) {
    throw new UnsupportedOperationException("not implemented");
  }
}
