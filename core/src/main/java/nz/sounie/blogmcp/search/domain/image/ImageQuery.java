package nz.sounie.blogmcp.search.domain.image;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The image a person searches with (search.md 3.8): a {@link LocalImage} or a {@link RemoteImage},
 * parsed from one piece of text. Each variant loads itself from a {@link QueryImageSource}, so no
 * caller asks which one it has.
 */
public sealed interface ImageQuery {

  /**
   * The largest image file or response body, in bytes (10 MiB), for query and post images alike.
   */
  int MAX_FILE_BYTES = 10_485_760;

  /**
   * Parses trimmed text: a {@link RemoteImage} when it matches {@code ^[A-Za-z][A-Za-z0-9+.-]*://},
   * otherwise a {@link LocalImage}.
   *
   * @throws InvalidImageQuery if the text is not a usable local image or https URL
   */
  static ImageQuery parse(String text) {
    String trimmed = Objects.requireNonNull(text, "text").strip();
    return QueryImageRules.URL_START.matcher(trimmed).lookingAt()
        ? RemoteImage.parse(trimmed)
        : LocalImage.parse(trimmed);
  }

  /** Loads the image's pixels from where this kind of image lives. */
  ImageFetch loadFrom(QueryImageSource source);

  /**
   * An absolute path to a readable regular file (links followed) named {@code .jpg}, {@code .jpeg},
   * {@code .png} or {@code .gif} in any case, of at most {@link #MAX_FILE_BYTES} bytes. The path is
   * kept as given: it is not normalised and links are not resolved.
   *
   * <p>The compact constructor checks the file at once (owner decision Q-IMG 13), so a bad path is
   * reported before any model is loaded.
   *
   * @throws InvalidImageQuery for the first rule the path breaks
   */
  record LocalImage(Path path) implements ImageQuery {
    public LocalImage {
      Objects.requireNonNull(path, "path");
      QueryImageRules.checkLocal(path);
    }

    private static LocalImage parse(String text) {
      try {
        return new LocalImage(Path.of(text));
      } catch (InvalidPathException e) {
        throw new InvalidImageQuery(
            InvalidImageQuery.Reason.NOT_ABSOLUTE_PATH, QueryImageRules.notAbsolute(text));
      }
    }

    @Override
    public ImageFetch loadFrom(QueryImageSource source) {
      return source.readFile(path);
    }
  }

  /**
   * An absolute {@code https} URL (scheme in any case) with a host. It needs no extension, because
   * the content type decides, and it is not resolved when parsed.
   *
   * @throws InvalidImageQuery if the scheme is not https ({@code NOT_HTTPS}) or there is no host
   *     ({@code MALFORMED_URL})
   */
  record RemoteImage(URI url) implements ImageQuery {
    public RemoteImage {
      Objects.requireNonNull(url, "url");
      QueryImageRules.checkRemote(url);
    }

    private static RemoteImage parse(String text) {
      try {
        return new RemoteImage(new URI(text));
      } catch (URISyntaxException e) {
        throw new InvalidImageQuery(
            InvalidImageQuery.Reason.MALFORMED_URL, QueryImageRules.malformed(text));
      }
    }

    @Override
    public ImageFetch loadFrom(QueryImageSource source) {
      return source.fetch(url);
    }
  }
}
