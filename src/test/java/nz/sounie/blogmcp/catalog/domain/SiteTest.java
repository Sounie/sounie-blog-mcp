package nz.sounie.blogmcp.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SiteTest {

  @Test
  void site_host_is_the_host_of_the_base_url() {
    assertThat(TestSites.SOUNIE_WP.host()).isEqualTo("blog2.sounie.nz");
  }
}
