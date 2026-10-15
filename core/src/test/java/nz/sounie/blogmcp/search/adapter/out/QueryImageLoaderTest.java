package nz.sounie.blogmcp.search.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageQuery;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-SRCH-84: {@link QueryImageLoader#readFile} over real files in a temporary directory and real
 * ImageIO. The decoding rules themselves are {@code ImageDecoding}'s (AC-SRCH-76 and 79); only
 * their wiring, the read-time byte cap and I/O failures are checked here.
 */
class QueryImageLoaderTest {

  private static final long MAX_BYTES = 10_485_760L;

  @TempDir Path dir;

  private final QueryImageLoader loader =
      new QueryImageLoader(
          new HttpImageSource(new PrintStream(new ByteArrayOutputStream(), true, UTF_8)));

  /** The local image, parsed (and so checked) before the file changes. */
  private static ImageQuery.LocalImage localImage(Path file) {
    return (ImageQuery.LocalImage) ImageQuery.parse(file.toString());
  }

  private ImageFetch read(Path file) {
    return loader.readFile(localImage(file).path());
  }

  private Path copyOfResource(String name) throws IOException {
    try (InputStream in = QueryImageLoaderTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("test resource %s", name).isNotNull();
      Path copy = dir.resolve(name);
      Files.copy(in, copy);
      return copy;
    }
  }

  private static void growTo(Path file, long bytes) throws IOException {
    try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
      out.setLength(bytes);
    }
  }

  @Test
  @DisplayName("AC-SRCH-84: a local PNG is decoded to pixels of its size")
  void decodes_a_local_image() throws IOException {
    Path swan = copyOfResource("black-swan-in-reeds.png");

    ImageFetch fetch = read(swan);

    assertThat(fetch)
        .isInstanceOfSatisfying(
            ImageFetch.Fetched.class,
            fetched -> {
              assertThat(fetched.pixels().width()).isEqualTo(768);
              assertThat(fetched.pixels().height()).isEqualTo(1024);
            });
  }

  @Test
  @DisplayName("AC-SRCH-84: a text file named .jpg is UNDECODABLE")
  void a_text_file_is_undecodable() throws IOException {
    Path fake = Files.writeString(dir.resolve("fake.jpg"), "this is text, not an image");

    assertThat(read(fake)).isEqualTo(new ImageFetch.Rejected(ImageRejection.UNDECODABLE));
  }

  @Test
  @DisplayName("AC-SRCH-84 (Q-IMG 11): a 32 x 32 PNG is TOO_SMALL; the 64 px floor applies")
  void a_tiny_image_is_too_small() throws IOException {
    Path tiny = Files.write(dir.resolve("tiny.png"), TestImages.png(32, 32));

    assertThat(read(tiny)).isEqualTo(new ImageFetch.Rejected(ImageRejection.TOO_SMALL));
  }

  @Test
  @DisplayName("AC-SRCH-84: a file that grew past 10 MiB after parsing is TOO_LARGE")
  void a_file_grown_past_the_cap_is_too_large() throws IOException {
    Path grown = Files.write(dir.resolve("grown.png"), TestImages.png(64, 64));
    ImageQuery.LocalImage image = localImage(grown);
    growTo(grown, MAX_BYTES + 1);

    assertThat(loader.readFile(image.path()))
        .isEqualTo(new ImageFetch.Rejected(ImageRejection.TOO_LARGE));
  }

  /**
   * A sparse file of 3 GiB costs no disk, but reading it whole would need an array larger than Java
   * allows ({@code Files.readAllBytes} throws {@code OutOfMemoryError}). Being refused proves the
   * read stops at the cap.
   */
  @Test
  @DisplayName("AC-SRCH-84: the read stops one byte past the cap, however large the file has grown")
  void the_read_is_bounded() throws IOException {
    Path grown = Files.write(dir.resolve("huge.png"), TestImages.png(64, 64));
    ImageQuery.LocalImage image = localImage(grown);
    growTo(grown, 3L * 1024 * 1024 * 1024);

    assertThat(loader.readFile(image.path()))
        .isEqualTo(new ImageFetch.Rejected(ImageRejection.TOO_LARGE));
  }

  @Test
  @DisplayName("AC-SRCH-84: a file of exactly 10 MiB is read")
  void a_file_of_exactly_the_cap_is_read() throws IOException {
    Path edge =
        Files.write(
            dir.resolve("edge.png"), TestImages.paddedTo(TestImages.png(64, 64), (int) MAX_BYTES));

    assertThat(read(edge)).isInstanceOf(ImageFetch.Fetched.class);
  }

  @Test
  @DisplayName("AC-SRCH-84: a file deleted after parsing is unavailable, naming the path")
  void a_deleted_file_is_unavailable() throws IOException {
    Path gone = Files.write(dir.resolve("gone.png"), TestImages.png(64, 64));
    ImageQuery.LocalImage image = localImage(gone);
    Files.delete(gone);

    assertThat(loader.readFile(image.path()))
        .isInstanceOfSatisfying(
            ImageFetch.Unavailable.class,
            unavailable -> assertThat(unavailable.detail()).contains(gone.toString()));
  }
}
