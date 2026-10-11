package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** AC-APP-7, in process: with the guard installed, a stray println cannot reach the protocol. */
@Isolated
class StdoutGuardTest {

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final ByteArrayOutputStream protocol = new ByteArrayOutputStream();
  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();

  @BeforeEach
  void givenSeparateStdoutAndStderr() {
    System.setOut(new PrintStream(protocol, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(diagnostics, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreTheRealStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  @DisplayName("AC-APP-7: System.out goes to stderr; only the transport writes to the real stdout")
  void a_stray_println_reaches_stderr_not_the_protocol_stream() {
    PrintStream forTransport = StdoutGuard.install();

    System.out.println("noise");
    forTransport.println("{\"jsonrpc\":\"2.0\"}");
    forTransport.flush();

    assertThat(diagnostics.toString(StandardCharsets.UTF_8)).contains("noise");
    assertThat(protocol.toString(StandardCharsets.UTF_8))
        .doesNotContain("noise")
        .isEqualTo("{\"jsonrpc\":\"2.0\"}" + System.lineSeparator());
  }
}
