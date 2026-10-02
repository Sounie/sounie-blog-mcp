package nz.sounie.blogmcp.catalog.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WebAddressTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not a url",
        "/2026/09/20/hello/",
        "blog2.sounie.nz/hello",
        "ftp://blog2.sounie.nz/x",
        "mailto:owner@example.com",
        ""
      })
  @DisplayName("AC-CAT-31: text that is not an absolute http(s) URL is an invalid reference")
  void rejects_text_that_is_not_an_absolute_web_url(String text) {
    assertThatThrownBy(() -> WebAddress.parse(text)).isInstanceOf(InvalidPostReference.class);
  }

  @ParameterizedTest
  @CsvSource({
    "http://blog2.sounie.nz/hello/, https://blog2.sounie.nz/hello/",
    "HTTP://blog2.sounie.nz/hello, https://blog2.sounie.nz/hello",
    "https://blog2.sounie.nz/hello?p=1, https://blog2.sounie.nz/hello?p=1",
    "http://blog2.sounie.nz/hello#comments, https://blog2.sounie.nz/hello",
    "http://blog2.sounie.nz:80/hello, https://blog2.sounie.nz/hello",
    "http://blog2.sounie.nz:8080/hello, https://blog2.sounie.nz:8080/hello",
    "https://blog2.sounie.nz:8443/hello, https://blog2.sounie.nz:8443/hello",
    "https://blog2.sounie.nz, https://blog2.sounie.nz"
  })
  @DisplayName("AC-CAT-30: http is treated as https; query and non-default ports are kept")
  void as_https_keeps_host_port_path_and_query(String text, String expected) {
    assertThat(WebAddress.parse(text).asHttps().value()).isEqualTo(URI.create(expected));
  }
}
