package nz.sounie.blogmcp.shared.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileTest {

  private static final byte[] CONTENT_A = ("{\"v\":\"" + "A".repeat(4_000) + "\"}").getBytes(UTF_8);
  private static final byte[] CONTENT_B = ("{\"v\":\"" + "B".repeat(9_000) + "\"}").getBytes(UTF_8);

  @TempDir Path directory;

  private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(errorBytes, true, UTF_8);

  @Test
  void writes_a_new_file_creating_missing_directories() throws IOException {
    Path target = directory.resolve("catalog").resolve("posts").resolve("x.json");

    AtomicFile.write(target, CONTENT_A);

    assertThat(Files.readAllBytes(target)).isEqualTo(CONTENT_A);
  }

  @Test
  @DisplayName("AC-APP-41: the atomic move replaces an existing file, leaving no temporary file")
  void replaces_an_existing_file() throws IOException {
    Path target = directory.resolve("x.json");
    AtomicFile.write(target, CONTENT_A);

    AtomicFile.write(target, CONTENT_B);

    assertThat(Files.readAllBytes(target)).isEqualTo(CONTENT_B);
    assertThat(directory.resolve("x.json.tmp")).doesNotExist();
    assertThat(filesIn(directory)).containsExactly("x.json");
  }

  @Test
  @DisplayName(
      "AC-APP-41: a concurrent reader only ever sees a complete A or B during 1,000 rewrites")
  void concurrent_reader_never_sees_a_missing_or_partial_file() throws Exception {
    Path target = directory.resolve("x.json");
    AtomicFile.write(target, CONTENT_A);
    AtomicBoolean writing = new AtomicBoolean(true);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      Future<?> writer =
          threads.submit(
              () -> {
                try {
                  start.await();
                  for (int i = 0; i < 1_000; i++) {
                    AtomicFile.write(target, i % 2 == 0 ? CONTENT_B : CONTENT_A);
                  }
                } finally {
                  writing.set(false);
                }
                return null;
              });
      Future<List<String>> reader =
          threads.submit(
              () -> {
                List<String> problems = new ArrayList<>();
                start.await();
                do {
                  problems.addAll(readProblem(target));
                } while (writing.get());
                return problems;
              });
      start.countDown();

      writer.get(2, TimeUnit.MINUTES);
      assertThat(reader.get(2, TimeUnit.MINUTES)).isEmpty();
    }
    assertThat(filesIn(directory)).containsExactly("x.json");
  }

  /** Empty if the file holds exactly A or B; otherwise what was wrong. */
  private static List<String> readProblem(Path target) throws IOException {
    try {
      byte[] read = Files.readAllBytes(target);
      return Arrays.equals(read, CONTENT_A) || Arrays.equals(read, CONTENT_B)
          ? List.of()
          : List.of("partial file of " + read.length + " bytes");
    } catch (NoSuchFileException e) {
      return List.of("missing file");
    }
  }

  @Test
  @DisplayName("AC-APP-26: a write that fails before the move leaves the target untouched")
  void failed_write_leaves_the_target_untouched() throws IOException {
    Path target = directory.resolve("x.json");
    AtomicFile.write(target, CONTENT_A);
    // The temporary sibling cannot be created: a non-empty directory occupies its name.
    Files.createDirectories(directory.resolve("x.json.tmp"));
    Files.writeString(directory.resolve("x.json.tmp").resolve("blocker"), "");

    assertThatThrownBy(() -> AtomicFile.write(target, CONTENT_B))
        .isInstanceOf(UncheckedIOException.class);

    assertThat(Files.readAllBytes(target)).isEqualTo(CONTENT_A);
  }

  @Test
  @DisplayName("AC-APP-26: sweep deletes leftover temporary files at any depth, and nothing else")
  void sweep_deletes_only_temporary_files() throws IOException {
    Path site = Files.createDirectories(directory.resolve("posts").resolve("sounie-wp"));
    Files.writeString(site.resolve("1.json"), "{}");
    Files.writeString(site.resolve("2.json.tmp"), "{\"partial");
    Files.writeString(directory.resolve("3.json.tmp"), "");
    Files.writeString(site.resolve("4.json.corrupt"), "x");

    AtomicFile.sweep(directory);

    assertThat(filesIn(site)).containsExactlyInAnyOrder("1.json", "4.json.corrupt");
    assertThat(directory.resolve("3.json.tmp")).doesNotExist();
  }

  @Test
  void sweep_of_a_missing_directory_does_nothing() {
    Path missing = directory.resolve("missing");

    AtomicFile.sweep(missing);

    assertThat(missing).doesNotExist();
  }

  @Test
  void delete_removes_the_file_and_ignores_a_missing_one() throws IOException {
    Path target = directory.resolve("x.json");
    Files.write(target, CONTENT_A);

    AtomicFile.delete(target);
    AtomicFile.delete(target);

    assertThat(target).doesNotExist();
  }

  @Test
  @DisplayName("AC-APP-27: quarantine renames to .corrupt and logs one line")
  void quarantine_renames_the_file_and_logs_one_line() throws IOException {
    Path file = directory.resolve("123.json");
    Files.writeString(file, "{\"trunc");

    AtomicFile.quarantine(file, "unexpected end of input", "will be fetched again", errors);

    assertThat(file).doesNotExist();
    assertThat(directory.resolve("123.json.corrupt")).hasContent("{\"trunc");
    assertThat(errorLines())
        .singleElement()
        .asString()
        .contains(file.toString(), "unexpected end of input", "will be fetched again");
  }

  @Test
  void quarantine_replaces_an_earlier_quarantined_copy() throws IOException {
    Path file = directory.resolve("123.json");
    Files.writeString(directory.resolve("123.json.corrupt"), "older");
    Files.writeString(file, "newer");

    AtomicFile.quarantine(file, "not JSON", "will be fetched again", errors);

    assertThat(directory.resolve("123.json.corrupt")).hasContent("newer");
    assertThat(file).doesNotExist();
  }

  private List<String> errorLines() {
    return errorBytes.toString(UTF_8).lines().toList();
  }

  private static List<String> filesIn(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(p -> p.getFileName().toString()).toList();
    }
  }
}
