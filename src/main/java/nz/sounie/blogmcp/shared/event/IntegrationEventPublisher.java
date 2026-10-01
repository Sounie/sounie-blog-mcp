package nz.sounie.blogmcp.shared.event;

/** Publishes integration events to interested contexts. */
public interface IntegrationEventPublisher {

  void publish(IntegrationEvent event);
}
