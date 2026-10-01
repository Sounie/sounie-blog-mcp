package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import nz.sounie.blogmcp.shared.event.IntegrationEvent;
import nz.sounie.blogmcp.shared.event.IntegrationEventPublisher;

/** Records every published integration event, in order. */
public final class RecordingEventPublisher implements IntegrationEventPublisher {

  private final List<IntegrationEvent> events = new CopyOnWriteArrayList<>();
  private volatile Consumer<IntegrationEvent> onPublish = event -> {};

  @Override
  public void publish(IntegrationEvent event) {
    onPublish.accept(event);
    events.add(event);
  }

  /** Runs a check at the moment each event is published (before it is recorded). */
  public void onPublish(Consumer<IntegrationEvent> check) {
    this.onPublish = check;
  }

  public List<IntegrationEvent> events() {
    return List.copyOf(events);
  }

  public <E extends IntegrationEvent> List<E> eventsOfType(Class<E> type) {
    return events.stream().filter(type::isInstance).map(type::cast).toList();
  }

  public void clear() {
    events.clear();
  }
}
