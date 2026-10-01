package nz.sounie.blogmcp.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InProcessEventBusTest {

  private static final CatalogPostWithdrawn WITHDRAWN =
      new CatalogPostWithdrawn(
          "sounie-wp:2", "sounie-wp", "https://blog2.sounie.nz/2/", "NO_LONGER_LISTED");

  private final InProcessEventBus bus = new InProcessEventBus();

  @Test
  void delivers_an_event_to_every_subscriber_of_its_type_in_subscription_order() {
    List<String> received = new ArrayList<>();
    bus.subscribe(CatalogPostWithdrawn.class, event -> received.add("first " + event.postId()));
    bus.subscribe(CatalogPostWithdrawn.class, event -> received.add("second " + event.postId()));

    bus.publish(WITHDRAWN);

    assertThat(received).containsExactly("first sounie-wp:2", "second sounie-wp:2");
  }

  @Test
  void does_not_deliver_events_of_other_types() {
    List<IntegrationEvent> received = new ArrayList<>();
    bus.subscribe(CatalogPostPublished.class, received::add);

    bus.publish(WITHDRAWN);

    assertThat(received).isEmpty();
  }

  @Test
  void a_subscriber_to_all_integration_events_receives_every_event() {
    List<IntegrationEvent> received = new ArrayList<>();
    bus.subscribe(IntegrationEvent.class, received::add);

    bus.publish(WITHDRAWN);

    assertThat(received).containsExactly(WITHDRAWN);
  }

  @Test
  void publishing_without_subscribers_does_nothing() {
    assertThatCode(() -> bus.publish(WITHDRAWN)).doesNotThrowAnyException();
  }
}
