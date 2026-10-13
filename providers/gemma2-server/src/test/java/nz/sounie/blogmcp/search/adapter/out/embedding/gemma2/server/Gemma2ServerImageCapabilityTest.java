package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Not tagged {@code local-server}: asking for the capability makes no network call. */
class Gemma2ServerImageCapabilityTest {

  @Test
  @DisplayName("AC-SRCH-55: the LM Studio provider is text-only, without calling the server")
  void has_no_image_embedder() {
    // Port 1 has no server; a network call would fail rather than answer.
    Gemma2Embedder embedder = new Gemma2Embedder("http://127.0.0.1:1/v1", Duration.ofSeconds(1));

    assertThat(embedder.imageEmbedder()).isEmpty();
  }
}
