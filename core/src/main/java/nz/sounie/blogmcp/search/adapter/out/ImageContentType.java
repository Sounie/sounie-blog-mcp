package nz.sounie.blogmcp.search.adapter.out;

import java.net.http.HttpHeaders;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The image types that are fetched: JPEG, PNG and GIF, by the response's {@code Content-Type}
 * (case-insensitive, parameters ignored). Anything else, including a missing header and WebP, is
 * not.
 */
enum ImageContentType {
  JPEG("image/jpeg"),
  PNG("image/png"),
  GIF("image/gif");

  private final String mediaType;

  ImageContentType(String mediaType) {
    this.mediaType = mediaType;
  }

  /** The type the headers declare; empty for a missing or unsupported content type. */
  static Optional<ImageContentType> of(HttpHeaders headers) {
    return headers
        .firstValue("Content-Type")
        .map(ImageContentType::mediaTypeOf)
        .flatMap(
            declared ->
                Stream.of(values()).filter(type -> type.mediaType.equals(declared)).findFirst());
  }

  private static String mediaTypeOf(String contentType) {
    return contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
  }
}
