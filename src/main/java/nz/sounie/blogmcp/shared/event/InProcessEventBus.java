package nz.sounie.blogmcp.shared.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Synchronous, in-process, at-most-once event bus. Delivers each event to every handler subscribed
 * to its type, in subscription order.
 */
public final class InProcessEventBus implements IntegrationEventPublisher {

  private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();

  public <E extends IntegrationEvent> void subscribe(Class<E> type, Consumer<? super E> handler) {
    subscriptions.add(new Subscription<>(type, handler));
  }

  @Override
  public void publish(IntegrationEvent event) {
    Objects.requireNonNull(event, "event");
    subscriptions.forEach(subscription -> subscription.deliver(event));
  }

  private record Subscription<E extends IntegrationEvent>(
      Class<E> type, Consumer<? super E> handler) {

    private Subscription {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(handler, "handler");
    }

    void deliver(IntegrationEvent event) {
      if (type.isInstance(event)) {
        handler.accept(type.cast(event));
      }
    }
  }
}
