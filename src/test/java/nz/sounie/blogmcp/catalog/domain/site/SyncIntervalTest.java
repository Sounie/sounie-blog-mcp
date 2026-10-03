package nz.sounie.blogmcp.catalog.domain.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** app.md 3.8: the interval value and its minimum. */
class SyncIntervalTest {

  @Test
  void the_default_is_twenty_four_hours() {
    assertThat(SyncInterval.DEFAULT).isEqualTo(SyncInterval.ofHours(24));
  }

  @Test
  void one_hour_is_the_shortest_interval() {
    assertThat(SyncInterval.ofHours(1).value()).isEqualTo(SyncInterval.MINIMUM);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 59 * 60 + 59})
  void anything_shorter_than_an_hour_is_rejected(long seconds) {
    assertThatThrownBy(() -> new SyncInterval(Duration.ofSeconds(seconds)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_negative_interval_is_rejected() {
    assertThatThrownBy(() -> new SyncInterval(Duration.ofHours(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void whole_hours_make_an_interval_of_that_many_hours() {
    assertThat(SyncInterval.ofHours(6).value()).isEqualTo(Duration.ofHours(6));
  }
}
