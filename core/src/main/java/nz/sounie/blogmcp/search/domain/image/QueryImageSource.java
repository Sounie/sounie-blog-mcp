package nz.sounie.blogmcp.search.domain.image;

import java.net.URI;
import java.nio.file.Path;

/**
 * Port: loads a query image's pixels, from a local file or an https URL (search.md 3.8). It lives
 * in the domain because {@link ImageQuery} calls it (ADR 0006).
 */
public interface QueryImageSource {

  /** Never throws for an expected outcome: rejections and read failures are values. */
  ImageFetch readFile(Path path);

  /** Never throws for an expected outcome: rejections and transient failures are values. */
  ImageFetch fetch(URI url);
}
