package nz.sounie.blogmcp.search.adapter.out;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.image.ImageSource;

/**
 * Fetches post images over HTTPS with the JDK HTTP client and decodes them with ImageIO (search.md
 * 3.7).
 *
 * <ul>
 *   <li>Any {@code https} host is allowed; there is no host allow-list.
 *   <li>Every address of the image host, and of each redirect target, must be permitted by the
 *       {@link BlogCategory} of the post's host, resolved once per fetch. Nothing is sent to a host
 *       that is not permitted.
 *   <li>Redirects are followed here, never by the client: at most {@value #MAX_REDIRECTS}.
 *   <li>Bodies are capped at {@value #MAX_BYTES} bytes, types at JPEG, PNG and GIF.
 *   <li>Each response, headers and body together, must complete within the response timeout;
 *       otherwise the exchange is cancelled and the fetch is {@code Unavailable}.
 * </ul>
 *
 * <p>Each outcome other than {@code Fetched} writes one line, with the URL and the reason, to the
 * error stream.
 *
 * <p>The address check uses {@link HostResolver}, while the client connects through the system
 * resolver, so a host whose DNS answer changes between the two is not caught (accepted residual
 * risk).
 */
public final class HttpImageSource implements ImageSource {

  static final int MAX_BYTES = 10 * 1024 * 1024;
  static final int MAX_REDIRECTS = 3;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);
  private static final String HTTPS = "https";
  private static final String USER_AGENT =
      "blog-mcp/0.1 (+https://github.com/Sounie/sounie-blog-mcp)";

  /** Checks on a response's headers, in order, before its body is kept. */
  private static final List<Function<HttpHeaders, Optional<ImageRejection>>> HEADER_RULES =
      List.of(
          headers ->
              Optional.of(ImageRejection.UNSUPPORTED_TYPE)
                  .filter(reason -> ImageContentType.of(headers).isEmpty()),
          headers ->
              Optional.of(ImageRejection.TOO_LARGE)
                  .filter(
                      reason -> headers.firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES));

  private final PrintStream errors;
  private final HostResolver resolver;
  private final HttpClient client;
  private final Duration responseTimeout;

  /** The system resolver, the default TLS context and a 30-second response timeout. */
  public HttpImageSource(PrintStream errors) {
    this(errors, HostResolver.system(), defaultTls(), RESPONSE_TIMEOUT);
  }

  HttpImageSource(
      PrintStream errors, HostResolver resolver, SSLContext tls, Duration responseTimeout) {
    this.errors = Objects.requireNonNull(errors, "errors");
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
    this.client =
        HttpClient.newBuilder()
            .sslContext(Objects.requireNonNull(tls, "tls"))
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  private static SSLContext defaultTls() {
    try {
      return SSLContext.getDefault();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No default TLS context", e);
    }
  }

  @Override
  public ImageFetch fetch(URI imageUrl, URI postUrl) {
    ImageFetch outcome = attempt(imageUrl, postUrl);
    warning(imageUrl, outcome).ifPresent(errors::println);
    return outcome;
  }

  private ImageFetch attempt(URI imageUrl, URI postUrl) {
    try {
      return hop(imageUrl, BlogCategory.of(addressesOf(postUrl)), MAX_REDIRECTS);
    } catch (IOException e) {
      // Includes timeouts, refused connections and hosts that do not resolve.
      return new ImageFetch.Unavailable(e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new ImageFetch.Unavailable("Interrupted while fetching");
    } catch (IllegalArgumentException e) {
      // A redirect location, or a URL, the client cannot request.
      return rejected(ImageRejection.HTTP_STATUS);
    }
  }

  /** One request, after the scheme and address checks; follows a redirect by recursion. */
  private ImageFetch hop(URI url, BlogCategory blog, int redirectsLeft)
      throws IOException, InterruptedException {
    if (!HTTPS.equalsIgnoreCase(url.getScheme())) {
      return rejected(ImageRejection.NOT_HTTPS);
    }
    if (!blog.permitsAll(addressesOf(url))) {
      return rejected(ImageRejection.ADDRESS_NOT_PERMITTED);
    }
    return respond(exchange(url), blog, redirectsLeft);
  }

  /**
   * The whole response, headers and body, within the response timeout; on expiry the exchange is
   * cancelled, so a server that drips its body cannot stall indexing.
   */
  private HttpResponse<Optional<byte[]>> exchange(URI url)
      throws IOException, InterruptedException {
    CompletableFuture<HttpResponse<Optional<byte[]>>> pending =
        client.sendAsync(request(url), HttpImageSource::bodyFor);
    try {
      return pending.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      pending.cancel(true);
      throw new HttpTimeoutException("No complete response within " + responseTimeout);
    } catch (ExecutionException e) {
      throw new IOException(e.getCause().toString(), e.getCause());
    }
  }

  /** A body the headers already refuse (or a redirect's, or an error's) is not kept. */
  private static HttpResponse.BodySubscriber<Optional<byte[]>> bodyFor(
      HttpResponse.ResponseInfo info) {
    return new CappedBody(refusal(info.headers()).isPresent() ? 0 : MAX_BYTES);
  }

  private static Optional<ImageRejection> refusal(HttpHeaders headers) {
    return HEADER_RULES.stream()
        .map(rule -> rule.apply(headers))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private List<InetAddress> addressesOf(URI url) throws UnknownHostException {
    String host =
        Optional.ofNullable(url.getHost())
            .orElseThrow(() -> new UnknownHostException("No host in " + url));
    return resolver.resolve(host);
  }

  private HttpRequest request(URI url) {
    return HttpRequest.newBuilder(url)
        .GET()
        .timeout(responseTimeout)
        .header("Accept", "image/jpeg, image/png, image/gif")
        .header("User-Agent", USER_AGENT)
        .build();
  }

  private ImageFetch respond(
      HttpResponse<Optional<byte[]>> response, BlogCategory blog, int redirectsLeft)
      throws IOException, InterruptedException {
    return switch (FetchStatus.of(response)) {
      case SUCCESS -> image(response.headers(), response.body());
      case REDIRECT -> redirect(response, blog, redirectsLeft);
      case TRANSIENT -> new ImageFetch.Unavailable("HTTP status " + response.statusCode());
      case PERMANENT -> rejected(ImageRejection.HTTP_STATUS);
    };
  }

  private ImageFetch redirect(HttpResponse<?> response, BlogCategory blog, int redirectsLeft)
      throws IOException, InterruptedException {
    return redirectsLeft == 0
        ? rejected(ImageRejection.TOO_MANY_REDIRECTS)
        : hop(location(response), blog, redirectsLeft - 1);
  }

  /** The {@code Location}, resolved against the URL that answered. */
  private static URI location(HttpResponse<?> response) {
    return response.uri().resolve(response.headers().firstValue("Location").orElseThrow());
  }

  /** The body is empty when it had more than {@link #MAX_BYTES} bytes. */
  private static ImageFetch image(HttpHeaders headers, Optional<byte[]> body) {
    Optional<ImageRejection> refused = refusal(headers);
    if (refused.isPresent()) {
      return rejected(refused.get());
    }
    return body.map(ImageDecoding::decode).orElseGet(() -> rejected(ImageRejection.TOO_LARGE));
  }

  private static ImageFetch rejected(ImageRejection reason) {
    return new ImageFetch.Rejected(reason);
  }

  private static Optional<String> warning(URI imageUrl, ImageFetch outcome) {
    return switch (outcome) {
      case ImageFetch.Fetched fetched -> Optional.empty();
      case ImageFetch.Rejected rejected ->
          Optional.of("search: image " + imageUrl + " skipped: " + rejected.reason().name());
      case ImageFetch.Unavailable unavailable ->
          Optional.of(
              "search: image " + imageUrl + " unavailable, will retry: " + unavailable.detail());
    };
  }
}
