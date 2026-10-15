package nz.sounie.blogmcp.search.adapter.out;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.image.QueryImageSource;

/**
 * Loads query images (search.md 3.8): local files through {@link ImageDecoding}, exactly like a
 * fetched body, and URLs through {@link HttpImageSource#fetchQueryImage}.
 *
 * <p>A local path was checked when its {@code LocalImage} was created, so reading it only guards
 * against the file having changed since: at most {@link HttpImageSource#MAX_BYTES} bytes plus one
 * are read, so a file that has grown past the cap is {@code TOO_LARGE}, and an I/O error is {@code
 * Unavailable}.
 */
public final class QueryImageLoader implements QueryImageSource {

  private static final ImageFetch TOO_LARGE = new ImageFetch.Rejected(ImageRejection.TOO_LARGE);

  private final HttpImageSource http;

  public QueryImageLoader(HttpImageSource http) {
    this.http = Objects.requireNonNull(http, "http");
  }

  @Override
  public ImageFetch readFile(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      byte[] bytes = in.readNBytes(HttpImageSource.MAX_BYTES + 1);
      return bytes.length > HttpImageSource.MAX_BYTES ? TOO_LARGE : ImageDecoding.decode(bytes);
    } catch (IOException e) {
      return new ImageFetch.Unavailable("cannot read " + path + ": " + e);
    }
  }

  @Override
  public ImageFetch fetch(URI url) {
    return http.fetchQueryImage(url);
  }
}
