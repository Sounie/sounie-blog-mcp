package nz.sounie.blogmcp.search.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import nz.sounie.blogmcp.search.adapter.out.InMemoryQueryImageSource;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * AC-SRCH-81: image query text parses to a local or a remote image, and a bad local file is refused
 * at once. The file checks run on the JDK's {@code Files}, which we don't own, so they are tested
 * against real files in a temporary directory.
 */
class ImageQueryTest {

  private static final int MAX_FILE_BYTES = 10_485_760;

  @TempDir Path dir;

  @BeforeEach
  void givenFiles() throws IOException {
    Files.write(dir.resolve("Swan.PNG"), new byte[100]);
    Files.write(dir.resolve("a.jpeg"), new byte[100]);
    Files.write(dir.resolve("a.gif"), new byte[100]);
    Files.write(dir.resolve("a.webp"), new byte[100]);
    Files.write(dir.resolve("notes.txt"), new byte[100]);
    Files.writeString(dir.resolve("fake.jpg"), "this is text, not an image");
    Files.createDirectory(dir.resolve("folder.png"));
    sized(dir.resolve("big.png"), MAX_FILE_BYTES + 1L);
    sized(dir.resolve("edge.png"), MAX_FILE_BYTES);
  }

  /** A file of exactly this many bytes (sparse where the file system allows). */
  private static void sized(Path file, long bytes) throws IOException {
    try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
      out.setLength(bytes);
    }
  }

  private String path(String name) {
    return dir.resolve(name).toString();
  }

  private static InvalidImageQuery rejection(String text) {
    InvalidImageQuery rejected =
        catchThrowableOfType(InvalidImageQuery.class, () -> ImageQuery.parse(text));
    assertThat(rejected).as("parsing %s is rejected", text).isNotNull();
    return rejected;
  }

  private static void assertRejected(String text, InvalidImageQuery.Reason reason) {
    InvalidImageQuery rejected = rejection(text);
    assertThat(rejected.reason()).as("reason for %s", text).isEqualTo(reason);
    assertThat(rejected.getMessage()).as("message quotes the text").contains(text.trim());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Swan.PNG", "a.jpeg", "a.gif", "edge.png", "fake.jpg"})
  @DisplayName(
      "AC-SRCH-81: an absolute path to a readable image file of at most 10 MiB is a local image")
  void parses_a_local_image(String name) {
    assertThat(ImageQuery.parse(path(name)))
        .isEqualTo(new ImageQuery.LocalImage(dir.resolve(name)));
  }

  @Test
  @DisplayName("AC-SRCH-81: the path is trimmed before it is checked")
  void trims_the_path() {
    assertThat(ImageQuery.parse("  " + path("Swan.PNG") + " \n"))
        .isEqualTo(new ImageQuery.LocalImage(dir.resolve("Swan.PNG")));
  }

  @Test
  @DisplayName("AC-SRCH-81: a link to a readable image file is followed")
  void follows_a_link_to_a_readable_file() throws IOException {
    Path link = Files.createSymbolicLink(dir.resolve("link.png"), dir.resolve("Swan.PNG"));

    assertThat(ImageQuery.parse(link.toString())).isEqualTo(new ImageQuery.LocalImage(link));
  }

  @Test
  @DisplayName(
      "AC-SRCH-81: an https URL is a remote image; it needs no extension and is not resolved")
  void parses_a_remote_image() {
    assertThat(ImageQuery.parse(" https://images.example.com/photo?id=7 "))
        .isEqualTo(new ImageQuery.RemoteImage(URI.create("https://images.example.com/photo?id=7")));
  }

  @Test
  @DisplayName("AC-SRCH-81: the scheme is matched in any case")
  void accepts_an_upper_case_https_scheme() {
    assertThat(ImageQuery.parse("HTTPS://images.example.com/a.png"))
        .isInstanceOf(ImageQuery.RemoteImage.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"swan.png", "~/swan.png", "", "   ", "data:image/png;base64,AAAA"})
  @DisplayName("AC-SRCH-81: text that is not an absolute path or a URL is NOT_ABSOLUTE_PATH")
  void rejects_a_path_that_is_not_absolute(String text) {
    assertRejected(text, InvalidImageQuery.Reason.NOT_ABSOLUTE_PATH);
  }

  @Test
  @DisplayName("AC-SRCH-81: the NOT_ABSOLUTE_PATH message says what an image must be")
  void the_not_absolute_message_says_what_is_expected() {
    assertThat(rejection("swan.png").getMessage())
        .contains("swan.png")
        .contains(
            "image must be an absolute path to a .jpg, .jpeg, .png or .gif file, or an https URL");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a.webp", "notes.txt", "a", "missing.webp"})
  @DisplayName(
      "AC-SRCH-81: a path without a supported extension is UNSUPPORTED_EXTENSION, whether or not"
          + " it exists")
  void rejects_an_unsupported_extension(String name) {
    assertRejected(path(name), InvalidImageQuery.Reason.UNSUPPORTED_EXTENSION);
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing.png", "folder.png"})
  @DisplayName("AC-SRCH-81: a missing file or a directory is NOT_A_READABLE_FILE")
  void rejects_what_is_not_a_regular_file(String name) {
    assertRejected(path(name), InvalidImageQuery.Reason.NOT_A_READABLE_FILE);
  }

  @Test
  @DisplayName("AC-SRCH-81: the NOT_A_READABLE_FILE message names the path")
  void the_not_readable_message_names_the_path() {
    assertThat(rejection(path("missing.png")).getMessage())
        .contains("no readable file at")
        .contains(path("missing.png"));
  }

  @Test
  @DisplayName("AC-SRCH-81: a file that cannot be read is NOT_A_READABLE_FILE (skipped as root)")
  void rejects_an_unreadable_file() throws IOException {
    Path locked = Files.write(dir.resolve("locked.png"), new byte[100]);
    assumeThat(Files.getFileStore(locked).supportsFileAttributeView("posix")).isTrue();
    Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
    assumeThat(Files.isReadable(locked)).as("not running as root").isFalse();

    assertRejected(locked.toString(), InvalidImageQuery.Reason.NOT_A_READABLE_FILE);
  }

  @Test
  @DisplayName("AC-SRCH-81: a file of 10,485,761 bytes is FILE_TOO_LARGE")
  void rejects_a_file_over_the_cap() {
    assertRejected(path("big.png"), InvalidImageQuery.Reason.FILE_TOO_LARGE);
    assertThat(rejection(path("big.png")).getMessage()).contains("is larger than 10 MB");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"http://example.com/a.png", "file:///tmp/a.png", "ftp://example.com/a.png"})
  @DisplayName("AC-SRCH-81: a URL with a scheme other than https is NOT_HTTPS")
  void rejects_a_url_that_is_not_https(String text) {
    assertRejected(text, InvalidImageQuery.Reason.NOT_HTTPS);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https:///a.png", "https://exa mple.com/a.png"})
  @DisplayName("AC-SRCH-81: an https URL that does not parse or has no host is MALFORMED_URL")
  void rejects_a_malformed_url(String text) {
    assertRejected(text, InvalidImageQuery.Reason.MALFORMED_URL);
  }

  @Test
  @DisplayName("Each variant loads itself: a local image reads its file")
  void a_local_image_reads_its_file() {
    ImagePixels pixels = InMemoryImageSource.pixels(1);
    InMemoryQueryImageSource source =
        new InMemoryQueryImageSource().reading(dir.resolve("Swan.PNG"), pixels);

    ImageFetch fetch = ImageQuery.parse(path("Swan.PNG")).loadFrom(source);

    assertThat(fetch).isEqualTo(new ImageFetch.Fetched(pixels));
    assertThat(source.filesRead()).containsExactly(dir.resolve("Swan.PNG"));
    assertThat(source.urlsFetched()).isEmpty();
  }

  @Test
  @DisplayName("Each variant loads itself: a remote image fetches its URL")
  void a_remote_image_fetches_its_url() {
    InMemoryQueryImageSource source =
        new InMemoryQueryImageSource()
            .rejecting("https://images.example.com/a.png", ImageRejection.UNSUPPORTED_TYPE);

    ImageFetch fetch = ImageQuery.parse("https://images.example.com/a.png").loadFrom(source);

    assertThat(fetch).isEqualTo(new ImageFetch.Rejected(ImageRejection.UNSUPPORTED_TYPE));
    assertThat(source.urlsFetched())
        .containsExactly(URI.create("https://images.example.com/a.png"));
    assertThat(source.filesRead()).isEmpty();
  }
}
