package nz.sounie.blogmcp.catalog.domain.site;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlatformTest {

  @ParameterizedTest
  @CsvSource({
    "wordpress, WORDPRESS",
    "WordPress, WORDPRESS",
    "WORDPRESS, WORDPRESS",
    "blogger, BLOGGER",
    "Blogger, BLOGGER"
  })
  void is_named_ignoring_case(String name, Platform expected) {
    assertThat(Platform.named(name)).contains(expected);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"ghost", "", " wordpress"})
  void an_unknown_or_missing_name_names_no_platform(String name) {
    assertThat(Platform.named(name)).isEmpty();
  }
}
