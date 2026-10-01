package nz.sounie.blogmcp.shared.event;

import java.util.function.Consumer;

/**
 * Synchronous, in-process, at-most-once event bus. Delivers each event to every handler subscribed
 * to its type, in subscription order.
 */
public final class InProcessEventBus implements IntegrationEventPublisher {

  public <E extends IntegrationEvent> void subscribe(Class<E> type, Consumer<? super E> handler) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public void publish(IntegrationEvent event) {
    throw new UnsupportedOperationException("not implemented");
  }
}
