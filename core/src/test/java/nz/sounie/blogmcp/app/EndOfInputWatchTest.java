package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** app.md 3.4 step 7: Main waits for end of input, then exits. */
class EndOfInputWatchTest {

  private final EndOfInputWatch watch =
      EndOfInputWatch.wrap(
          new ByteArrayInputStream("{\"jsonrpc\":\"2.0\"}\n".getBytes(StandardCharsets.UTF_8)));

  @Test
  void passes_every_byte_through_unchanged() throws IOException {
    try (InputStream in = watch.stream()) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("{\"jsonrpc\":\"2.0\"}\n");
    }
  }

  @Test
  void has_not_ended_while_input_remains() throws InterruptedException {
    assertThat(watch.awaitEnd(Duration.ofMillis(50))).isFalse();
  }

  @Test
  void ends_when_the_reader_reaches_end_of_file() throws IOException, InterruptedException {
    InputStream in = watch.stream();
    in.readAllBytes();

    assertThat(watch.awaitEnd(Duration.ofSeconds(1))).isTrue();
  }
}
