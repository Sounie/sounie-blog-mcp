package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;
import org.junit.jupiter.api.Test;

class SiteChoicesTest {

  private final SiteChoices choices = SiteChoices.of(List.of("sounie-wp", "elegant"));

  @Test
  void keeps_the_configured_order() {
    assertThat(choices.ids()).containsExactly("sounie-wp", "elegant");
  }

  @Test
  void a_configured_site_filters_to_that_site() {
    assertThat(choices.filterFor("elegant"))
        .isEqualTo(new SiteFilter.OnlySite(new SiteId("elegant")));
  }

  @Test
  void an_unknown_site_is_rejected_listing_the_known_ones() {
    assertThatThrownBy(() -> choices.filterFor("nope"))
        .isInstanceOf(InvalidToolArgument.class)
        .hasMessageContaining("site")
        .hasMessageContaining("nope")
        .hasMessageContaining("sounie-wp")
        .hasMessageContaining("elegant");
  }
}
