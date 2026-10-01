package nz.sounie.blogmcp.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChangeOrderTest {

  private final AtomicInteger advances = new AtomicInteger();

  @Test
  @DisplayName("AC-CAT-18: an oldest-first source advances the checkpoint after every page")
  void oldest_first_advances_the_checkpoint_after_every_page() {
    ChangeOrder.OLDEST_FIRST.afterPageHandled(advances::incrementAndGet);

    assertThat(advances).hasValue(1);
  }

  @ParameterizedTest
  @EnumSource(
      value = ChangeOrder.class,
      names = {"NEWEST_FIRST", "UNORDERED"})
  @DisplayName("AC-CAT-4 / AC-CAT-19: newest-first and unordered sources never advance mid-run")
  void other_orders_do_not_advance_the_checkpoint_after_a_page(ChangeOrder order) {
    order.afterPageHandled(advances::incrementAndGet);

    assertThat(advances).hasValue(0);
  }

  @ParameterizedTest
  @EnumSource(ChangeOrder.class)
  void every_order_advances_the_checkpoint_after_the_last_page(ChangeOrder order) {
    order.afterLastPage(advances::incrementAndGet);

    assertThat(advances).hasValue(1);
  }
}
