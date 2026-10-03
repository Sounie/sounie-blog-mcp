package nz.sounie.blogmcp.catalog.domain.site;

import static nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind.INVALID_SYNC_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** app.md 3.8: each variant's rule, tested on the variant. */
class SyncIntervalSettingTest {

  @Test
  @DisplayName("AC-APP-10: an omitted interval is valid and defaults to 24 hours")
  void omitted_is_valid_and_gives_the_default() {
    SyncIntervalSetting omitted = new SyncIntervalSetting.Omitted();

    assertThat(omitted.violation()).isEmpty();
    assertThat(omitted.toInterval()).isEqualTo(SyncInterval.DEFAULT);
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 6, 24, 24 * 365})
  void whole_hours_of_at_least_one_are_valid(long hours) {
    SyncIntervalSetting setting = new SyncIntervalSetting.WholeHours(hours);

    assertThat(setting.violation()).isEmpty();
    assertThat(setting.toInterval().value()).isEqualTo(Duration.ofHours(hours));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, -24})
  @DisplayName("AC-APP-8: fewer than one hour is a violation naming the value found")
  void fewer_than_one_hour_is_a_violation(long hours) {
    SyncIntervalSetting setting = new SyncIntervalSetting.WholeHours(hours);

    assertThat(setting.violation())
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.kind()).isEqualTo(INVALID_SYNC_INTERVAL);
              assertThat(violation.detail())
                  .contains("syncEveryHours")
                  .contains("at least 1")
                  .contains(String.valueOf(hours));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"24\"", "24.5", "true", "null", "{}"})
  @DisplayName("AC-APP-8: an unparseable value is always a violation naming the value found")
  void an_unparseable_value_is_a_violation(String text) {
    SyncIntervalSetting setting = new SyncIntervalSetting.Unparseable(text);

    assertThat(setting.violation())
        .hasValueSatisfying(
            violation -> {
              assertThat(violation.kind()).isEqualTo(INVALID_SYNC_INTERVAL);
              assertThat(violation.detail())
                  .contains("syncEveryHours")
                  .contains("whole number of hours")
                  .contains(text);
            });
  }
}
