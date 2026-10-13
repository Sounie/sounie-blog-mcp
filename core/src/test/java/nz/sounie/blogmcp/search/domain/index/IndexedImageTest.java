package nz.sounie.blogmcp.search.domain.index;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Invariant 7 (search.md 3.1), the part an indexed image checks on its own. */
class IndexedImageTest {

  @Test
  @DisplayName("Invariant 7: an indexed image has an https URL")
  void rejects_a_url_that_is_not_https() {
    assertThatThrownBy(
            () ->
                new IndexedImage(
                    0, URI.create("http://x.example/a.jpg"), "Swan", Vectors.axis(384, 1)))
        .isInstanceOf(InvalidIndexedPost.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  @DisplayName("Invariant 7: an indexed image has a non-blank description")
  void rejects_a_blank_description(String description) {
    assertThatThrownBy(
            () ->
                new IndexedImage(
                    0, URI.create("https://x.example/a.jpg"), description, Vectors.axis(384, 1)))
        .isInstanceOf(InvalidIndexedPost.class);
  }
}
