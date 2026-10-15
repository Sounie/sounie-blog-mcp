package nz.sounie.blogmcp.search.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** AC-SRCH-83: each image rejection owns a one-line explanation for the person searching. */
class ImageRejectionTest {

  @ParameterizedTest
  @EnumSource(ImageRejection.class)
  @DisplayName("AC-SRCH-83: every rejection has a non-blank, one-line explanation")
  void every_rejection_explains_itself(ImageRejection reason) {
    assertThat(reason.explanation()).isNotBlank().doesNotContain("\n");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "NOT_HTTPS            | it redirects to a URL that is not https",
        "TOO_MANY_REDIRECTS   | it redirects more than 3 times",
        "HTTP_STATUS          | the server answered with an error status",
        "TOO_LARGE            | it is larger than 10 MB or 16 megapixels",
        "UNSUPPORTED_TYPE     | it is not a JPEG, PNG or GIF image (WebP is not supported)",
        "UNDECODABLE          | it cannot be read as a JPEG, PNG or GIF image",
        "TOO_SMALL            | it is smaller than 64 pixels on a side"
      })
  @DisplayName("AC-SRCH-83: the explanations are the texts of search.md 3.8")
  void explains_with_the_modelled_text(ImageRejection reason, String explanation) {
    assertThat(reason.explanation()).isEqualTo(explanation);
  }

  @Test
  @DisplayName("AC-SRCH-83: ADDRESS_NOT_PERMITTED says the host's address may not be fetched")
  void address_not_permitted_explains_the_address_rule() {
    assertThat(ImageRejection.ADDRESS_NOT_PERMITTED.explanation())
        .startsWith("its host's address is not one this server may fetch");
  }
}
