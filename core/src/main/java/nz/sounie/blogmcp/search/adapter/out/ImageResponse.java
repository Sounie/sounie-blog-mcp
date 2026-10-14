package nz.sounie.blogmcp.search.adapter.out;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * What one image request answered: the status, the headers, the URL that answered and the body. The
 * body is empty when it was cut short (refused at the headers, or over the size cap).
 */
record ImageResponse(int statusCode, HttpHeaders headers, URI uri, Optional<byte[]> body) {

  /** A response the client delivered in full (its body may still have been cut to empty). */
  static ImageResponse of(HttpResponse<Optional<byte[]>> response) {
    return new ImageResponse(
        response.statusCode(), response.headers(), response.uri(), response.body());
  }

  /**
   * A response whose body we cut short, built from its status and headers alone: the client may
   * fail the exchange once the body is cancelled, but the outcome no longer depends on the body.
   */
  static ImageResponse cut(HttpResponse.ResponseInfo info, URI uri) {
    return new ImageResponse(info.statusCode(), info.headers(), uri, Optional.empty());
  }
}
