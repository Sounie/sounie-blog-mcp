package nz.sounie.blogmcp.search.adapter.in;

import java.io.PrintStream;
import nz.sounie.blogmcp.search.application.IndexPost;
import nz.sounie.blogmcp.shared.event.InProcessEventBus;
import nz.sounie.blogmcp.shared.event.IntegrationEvent;

/**
 * Keeps the index in step with catalog events. Isolates failures: any exception from translation or
 * indexing is logged to the error stream and swallowed, so a catalog sync never fails because of
 * search.
 */
public final class CatalogEventListener {

  public CatalogEventListener(IndexPost indexPost, PrintStream errors) {}

  /** Subscribes to every catalog integration event. */
  public void subscribeTo(InProcessEventBus bus) {
    throw new UnsupportedOperationException("not implemented");
  }

  public void on(IntegrationEvent event) {
    throw new UnsupportedOperationException("not implemented");
  }
}
