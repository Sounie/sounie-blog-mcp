package nz.sounie.blogmcp.search.adapter.in;

import java.io.PrintStream;
import java.util.Objects;
import nz.sounie.blogmcp.search.application.IndexPost;
import nz.sounie.blogmcp.shared.event.InProcessEventBus;
import nz.sounie.blogmcp.shared.event.IntegrationEvent;

/**
 * Keeps the index in step with catalog events. Isolates failures: any exception from translation or
 * indexing is logged to the error stream and swallowed, so a catalog sync never fails because of
 * search.
 */
public final class CatalogEventListener {

  private final IndexPost indexPost;
  private final PrintStream errors;

  public CatalogEventListener(IndexPost indexPost, PrintStream errors) {
    this.indexPost = Objects.requireNonNull(indexPost, "indexPost");
    this.errors = Objects.requireNonNull(errors, "errors");
  }

  /** Subscribes to every catalog integration event. */
  public void subscribeTo(InProcessEventBus bus) {
    bus.subscribe(IntegrationEvent.class, this::on);
  }

  public void on(IntegrationEvent event) {
    try {
      indexPost.apply(CatalogEventTranslation.toChange(event));
    } catch (RuntimeException e) {
      // The bus is synchronous: rethrowing would abort the catalog sync. The next reconcile
      // repairs.
      errors.println(
          "search: could not index catalog post "
              + CatalogEventTranslation.postIdOf(event)
              + " ("
              + event.getClass().getSimpleName()
              + "): "
              + e);
    }
  }
}
