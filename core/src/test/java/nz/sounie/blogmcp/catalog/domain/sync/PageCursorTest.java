package nz.sounie.blogmcp.catalog.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageCursorTest {

  @Test
  void is_one_based() {
    assertThatThrownBy(() -> new PageCursor(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void may_be_one() {
    assertThat(new PageCursor(1).value()).isEqualTo(1);
  }

  @Test
  void first_is_page_one() {
    assertThat(PageCursor.first()).isEqualTo(new PageCursor(1));
  }
}
