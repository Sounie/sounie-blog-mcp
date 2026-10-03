package nz.sounie.blogmcp.app;

import io.modelcontextprotocol.client.transport.ServerParameters;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Starts the server as a real subprocess: {@code java … nz.sounie.blogmcp.app.Main} on the main
 * runtime classpath, or {@code java -jar} on the shadow jar, with {@code BLOG_MCP_CONFIG} and
 * {@code BLOG_MCP_DATA} pointing at test files.
 */
final class ServerProcess {

  static final String MAIN_CLASS = "nz.sounie.blogmcp.app.Main";

  private ServerProcess() {}

  /** The java launcher of the JVM running the tests. */
  static String java() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  /**
   * JVM options every subprocess needs in the sandbox: DJL's native cache and the temp directory
   * must be writable (as in the test task itself).
   */
  static List<String> sandboxOptions() {
    List<String> options = new ArrayList<>();
    options.add("-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"));
    String djlCache = System.getProperty("DJL_CACHE_DIR");
    if (djlCache != null) {
      options.add("-DDJL_CACHE_DIR=" + djlCache);
    }
    return options;
  }

  /** Arguments that run {@code Main} from the main runtime classpath (set by the test task). */
  static List<String> mainArguments() {
    String classpath =
        Objects.requireNonNullElse(
            System.getProperty("blogmcp.runtimeClasspath"), System.getProperty("java.class.path"));
    List<String> args = new ArrayList<>(sandboxOptions());
    args.addAll(List.of("--enable-native-access=ALL-UNNAMED", "-cp", classpath, MAIN_CLASS));
    return args;
  }

  /**
   * The server's configuration variables. {@code JAVA_TOOL_OPTIONS} is blanked: in the sandbox it
   * carries the proxy credentials, which the child JVM would echo to stderr (and so into test
   * reports). The child needs no proxy; {@link #sandboxOptions()} passes what it does need.
   */
  static Map<String, String> environment(Path config, Path data) {
    return Map.of(
        "BLOG_MCP_CONFIG",
        config.toString(),
        "BLOG_MCP_DATA",
        data.toString(),
        "JAVA_TOOL_OPTIONS",
        "");
  }

  /** For the real {@code McpClient} over {@code StdioClientTransport}. */
  static ServerParameters parameters(List<String> args, Path config, Path data) {
    return ServerParameters.builder(java()).args(args).env(environment(config, data)).build();
  }

  /** A raw subprocess, for tests that must see every byte on stdout. */
  static Process start(List<String> args, Path config, Path data) {
    List<String> command = new ArrayList<>();
    command.add(java());
    command.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().putAll(environment(config, data));
    builder.environment().remove("JAVA_TOOL_OPTIONS");
    try {
      return builder.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Polls until the condition holds, failing with the description after the timeout. */
  static void eventually(Duration timeout, String what, BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out after " + timeout + " waiting for " + what);
      }
      Thread.sleep(20);
    }
  }

  /** Collects a stream's lines on a daemon thread. */
  static List<String> collectLines(InputStream stream) {
    List<String> lines = new CopyOnWriteArrayList<>();
    Thread.ofPlatform()
        .daemon()
        .start(
            () -> {
              try (BufferedReader in =
                  new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                in.lines().forEach(lines::add);
              } catch (IOException | UncheckedIOException e) {
                lines.add("<<read failed: " + e + ">>");
              }
            });
    return lines;
  }
}
