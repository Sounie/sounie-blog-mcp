package nz.sounie.blogmcp.catalog.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OverlapMarginTest {

  @Test
  void must_not_be_negative() {
    assertThatThrownBy(() -> new OverlapMargin(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void may_be_zero() {
    assertThat(new OverlapMargin(Duration.ZERO).value()).isZero();
  }
}
