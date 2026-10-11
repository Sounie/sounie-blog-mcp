package nz.sounie.blogmcp.search.domain.query;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.domain.post.SiteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SiteFilterTest {

  private static final SiteId SOUNIE_WP = new SiteId("sounie-wp");
  private static final SiteId ELEGANT = new SiteId("elegant");

  @Test
  @DisplayName("AC-SRCH-26: AnySite includes every site")
  void any_site_includes_every_site() {
    SiteFilter filter = new SiteFilter.AnySite();

    assertThat(filter.includes(SOUNIE_WP)).isTrue();
    assertThat(filter.includes(ELEGANT)).isTrue();
  }

  @Test
  @DisplayName("AC-SRCH-26: OnlySite includes only that site")
  void only_site_includes_only_that_site() {
    SiteFilter filter = new SiteFilter.OnlySite(ELEGANT);

    assertThat(filter.includes(ELEGANT)).isTrue();
    assertThat(filter.includes(SOUNIE_WP)).isFalse();
  }
}
