package nz.sounie.blogmcp.catalog.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrl;

/** An absolute http(s) URL given by a caller of {@link GetPost}, parsed once. */
record WebAddress(URI uri) {

  private static final Set<String> WEB_SCHEMES = Set.of("http", "https");
  private static final int DEFAULT_HTTP_PORT = 80;

  WebAddress {
    Objects.requireNonNull(uri, "uri");
    if (!isWebUrl(uri)) {
      throw notAUrl(uri.toString());
    }
  }

  /**
   * @throws InvalidPostReference if the text is not an absolute http(s) URL
   */
  static WebAddress parse(String text) {
    try {
      return new WebAddress(new URI(text));
    } catch (URISyntaxException e) {
      throw notAUrl(text);
    }
  }

  /** The same address as https, so that it can be compared with canonical URLs. */
  CanonicalUrl asHttps() {
    String path = Objects.requireNonNullElse(uri.getRawPath(), "");
    return new CanonicalUrl(
        URI.create("https://" + uri.getHost() + portPart() + path + queryPart()));
  }

  private static boolean isWebUrl(URI uri) {
    String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
    return WEB_SCHEMES.contains(scheme) && uri.getHost() != null;
  }

  /** Empty for no port, or for http's default port, which https does not share. */
  private String portPart() {
    boolean defaultHttpPort =
        "http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == DEFAULT_HTTP_PORT;
    return uri.getPort() == -1 || defaultHttpPort ? "" : ":" + uri.getPort();
  }

  private String queryPart() {
    return Optional.ofNullable(uri.getRawQuery()).map(query -> "?" + query).orElse("");
  }

  private static InvalidPostReference notAUrl(String text) {
    return new InvalidPostReference("Not an absolute http(s) URL: '" + text + "'");
  }
}
