package nz.sounie.blogmcp.search.adapter.out;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link HttpImageSource} against a real HTTPS server (WireMock, with a self-signed localhost
 * certificate the client is told to trust), the real JDK HTTP client and real ImageIO. Only our own
 * {@link HostResolver} seam is replaced, by {@link FixedHostResolver}.
 */
class HttpImageSourceTest {

  private static final String KEYSTORE = "localhost-tls.p12";
  private static final String KEYSTORE_PASSWORD = "test-only";
  private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(2);
  private static final int MAX_BYTES = 10_485_760;

  @RegisterExtension
  static final WireMockExtension server =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .httpDisabled(true)
                  .dynamicHttpsPort()
                  .keystorePath(keystorePath())
                  .keystoreType("PKCS12")
                  .keystorePassword(KEYSTORE_PASSWORD)
                  .keyManagerPassword(KEYSTORE_PASSWORD)
                  .useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.ALWAYS))
          .build();

  /** A loopback blog: the post host resolves to 127.0.0.1. */
  private static final URI LOOPBACK_POST = URI.create("https://localhost/p");

  /** A public blog, by IP literal (search.md 3.7). */
  private static final URI PUBLIC_POST = URI.create("https://93.184.216.34/p");

  private static final URI PRIVATE_POST = URI.create("https://blog.lan/p");
  private static final URI NAMED_LOOPBACK_POST = URI.create("https://blog.example/p");
  private static final URI NON_ROUTABLE_POST = URI.create("https://169.254.10.10/p");

  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

  private static String keystorePath() {
    try {
      return Path.of(HttpImageSourceTest.class.getResource(KEYSTORE).toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /** TLS that trusts exactly the test certificate. */
  private static SSLContext trustingTheTestCertificate() {
    try (InputStream in = HttpImageSourceTest.class.getResourceAsStream(KEYSTORE)) {
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(in, KEYSTORE_PASSWORD.toCharArray());
      TrustManagerFactory trust =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trust.init(store);
      SSLContext tls = SSLContext.getInstance("TLS");
      tls.init(null, trust.getTrustManagers(), null);
      return tls;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The default resolver: the WireMock host and the post hosts above, as their names suggest. */
  private static FixedHostResolver resolver() {
    return new FixedHostResolver()
        .resolving("localhost", "127.0.0.1")
        .resolving("blog.lan", "192.168.1.10")
        .resolving("blog.example", "127.0.0.1");
  }

  private HttpImageSource source(FixedHostResolver resolver) {
    return new HttpImageSource(
        new PrintStream(stderr, true, UTF_8),
        resolver,
        trustingTheTestCertificate(),
        RESPONSE_TIMEOUT);
  }

  private ImageFetch fetch(String path, URI postUrl) {
    return fetch(path, postUrl, resolver());
  }

  private ImageFetch fetch(String path, URI postUrl, FixedHostResolver resolver) {
    return source(resolver).fetch(url(path), postUrl);
  }

  private static URI url(String path) {
    return URI.create("https://localhost:" + server.getHttpsPort() + path);
  }

  private static void serve(String path, ResponseDefinitionBuilder response) {
    server.stubFor(get(urlEqualTo(path)).willReturn(response));
  }

  private static ResponseDefinitionBuilder image(String contentType, byte[] body) {
    return aResponse().withStatus(200).withHeader("Content-Type", contentType).withBody(body);
  }

  private static void redirect(String from, String to) {
    serve(from, temporaryRedirect(to));
  }

  private static ImagePixels fetched(ImageFetch fetch) {
    assertThat(fetch).isInstanceOf(ImageFetch.Fetched.class);
    return ((ImageFetch.Fetched) fetch).pixels();
  }

  private static void assertRejected(ImageFetch fetch, ImageRejection reason) {
    assertThat(fetch).isEqualTo(new ImageFetch.Rejected(reason));
  }

  private static void assertNoRequestSent() {
    assertThat(server.getAllServeEvents()).isEmpty();
  }

  private List<String> stderrLines() {
    return stderr.toString(UTF_8).lines().toList();
  }

  @Nested
  class Decoding {

    @Test
    @DisplayName("AC-SRCH-76: a JPEG is fetched as RGB pixels of its size")
    void fetches_a_jpeg() {
      serve(
          "/photo.jpg", image("image/jpeg", TestImages.jpeg(TestImages.solid(100, 80, 0x808080))));

      ImagePixels pixels = fetched(fetch("/photo.jpg", LOOPBACK_POST));

      assertThat(pixels.width()).isEqualTo(100);
      assertThat(pixels.height()).isEqualTo(80);
    }

    @Test
    @DisplayName("AC-SRCH-76: a PNG's alpha is ignored, not composited on a background")
    void a_png_alpha_channel_is_dropped() {
      serve(
          "/translucent.png",
          image("image/png", TestImages.png(TestImages.translucent(100, 80, 0x40, 0xC86432))));

      ImagePixels pixels = fetched(fetch("/translucent.png", LOOPBACK_POST));

      assertThat(pixels.width()).isEqualTo(100);
      assertThat(pixels.height()).isEqualTo(80);
      assertThat(pixels.rgb()).isEqualTo(TestImages.rgbOf(100, 80, 0xC86432));
    }

    @Test
    @DisplayName("AC-SRCH-76: an animated GIF gives its first frame")
    void an_animated_gif_gives_its_first_frame() {
      serve(
          "/animated.gif",
          image(
              "image/gif",
              TestImages.animatedGif(
                  TestImages.solid(64, 64, 0xFF0000), TestImages.solid(64, 64, 0x0000FF))));

      ImagePixels pixels = fetched(fetch("/animated.gif", LOOPBACK_POST));

      assertThat(pixels.width()).isEqualTo(64);
      assertThat(pixels.height()).isEqualTo(64);
      assertThat(pixels.rgb()).isEqualTo(TestImages.rgbOf(64, 64, 0xFF0000));
    }
  }

  @Nested
  class Scheme {

    @Test
    @DisplayName("AC-SRCH-77: images come from host names unrelated to the post's host")
    void fetches_from_any_https_host() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));
      HttpImageSource source = source(resolver());

      assertThat(source.fetch(url("/a.png"), NAMED_LOOPBACK_POST))
          .isInstanceOf(ImageFetch.Fetched.class);
      assertThat(
              source.fetch(
                  URI.create("https://127.0.0.1:" + server.getHttpsPort() + "/a.png"),
                  NAMED_LOOPBACK_POST))
          .isInstanceOf(ImageFetch.Fetched.class);
    }

    @Test
    @DisplayName("AC-SRCH-77: an http image URL is rejected without any request")
    void an_http_url_is_rejected_without_a_request() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));

      ImageFetch fetch =
          source(resolver())
              .fetch(
                  URI.create("http://localhost:" + server.getHttpsPort() + "/a.png"),
                  LOOPBACK_POST);

      assertRejected(fetch, ImageRejection.NOT_HTTPS);
      assertNoRequestSent();
    }
  }

  @Nested
  class BlogCategory {

    @ParameterizedTest
    @ValueSource(strings = {"10.1.2.3", "127.0.0.1"})
    @DisplayName(
        "AC-SRCH-78: a public blog's image on a private-network or loopback address is rejected,"
            + " with no request")
    void a_public_blog_rejects_internal_image_addresses(String imageAddress) {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));

      ImageFetch fetch =
          fetch("/a.png", PUBLIC_POST, resolver().resolving("localhost", imageAddress));

      assertRejected(fetch, ImageRejection.ADDRESS_NOT_PERMITTED);
      assertNoRequestSent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"internal.example", "127.0.0.1"})
    @DisplayName(
        "AC-SRCH-78: a public image redirecting to an internal address is rejected; the target"
            + " gets no request")
    void a_redirect_to_an_internal_address_is_rejected(String targetHost) {
      serve("/secret.png", image("image/png", TestImages.png(64, 64)));
      redirect("/a.png", "https://" + targetHost + ":" + server.getHttpsPort() + "/secret.png");
      FixedHostResolver resolver =
          resolver()
              .resolving("localhost", "93.184.216.34")
              .resolving("internal.example", "10.0.0.7");

      ImageFetch fetch = fetch("/a.png", PUBLIC_POST, resolver);

      assertRejected(fetch, ImageRejection.ADDRESS_NOT_PERMITTED);
      server.verify(0, getRequestedFor(urlEqualTo("/secret.png")));
    }

    @Test
    @DisplayName("AC-SRCH-78: a loopback blog may use a loopback image")
    void a_loopback_blog_fetches_a_loopback_image() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));

      assertThat(fetch("/a.png", LOOPBACK_POST)).isInstanceOf(ImageFetch.Fetched.class);
    }

    @ParameterizedTest
    @MethodSource("nz.sounie.blogmcp.search.adapter.out.HttpImageSourceTest#internalBlogs")
    @DisplayName("AC-SRCH-78: a private-network or loopback blog may use a public image")
    void an_internal_blog_fetches_a_public_image(URI postUrl) {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));

      ImageFetch fetch =
          fetch("/a.png", postUrl, resolver().resolving("localhost", "93.184.216.34"));

      assertThat(fetch).isInstanceOf(ImageFetch.Fetched.class);
    }

    @ParameterizedTest
    @MethodSource("nz.sounie.blogmcp.search.adapter.out.HttpImageSourceTest#routableBlogs")
    @DisplayName(
        "AC-SRCH-78: a public, private-network or loopback blog never fetches a link-local image")
    void a_routable_blog_rejects_a_link_local_image(URI postUrl) {
      ImageFetch fetch =
          source(resolver()).fetch(URI.create("https://169.254.169.254/a.jpg"), postUrl);

      assertRejected(fetch, ImageRejection.ADDRESS_NOT_PERMITTED);
    }

    @Test
    @DisplayName("AC-SRCH-78 (owner change): a non-routable blog may use a non-routable image")
    void a_non_routable_blog_fetches_a_non_routable_image() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));

      ImageFetch fetch =
          fetch("/a.png", NON_ROUTABLE_POST, resolver().resolving("localhost", "169.254.20.20"));

      assertThat(fetch).isInstanceOf(ImageFetch.Fetched.class);
    }

    @Test
    @DisplayName("Blog category: a post host with any public address counts as a public blog")
    void the_least_permissive_post_address_decides() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));
      FixedHostResolver resolver =
          resolver().resolving("blog.example", "127.0.0.1", "93.184.216.34");

      ImageFetch fetch = fetch("/a.png", NAMED_LOOPBACK_POST, resolver);

      assertRejected(fetch, ImageRejection.ADDRESS_NOT_PERMITTED);
      assertNoRequestSent();
    }

    @Test
    @DisplayName("Blog category: every address of the image host must be permitted")
    void every_image_address_must_be_permitted() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));
      FixedHostResolver resolver = resolver().resolving("localhost", "93.184.216.34", "10.0.0.1");

      ImageFetch fetch = fetch("/a.png", PUBLIC_POST, resolver);

      assertRejected(fetch, ImageRejection.ADDRESS_NOT_PERMITTED);
      assertNoRequestSent();
    }

    @Test
    @DisplayName("AC-SRCH-78: a post host that does not resolve makes the fetch unavailable")
    void an_unresolvable_post_host_is_unavailable() {
      serve("/a.png", image("image/png", TestImages.png(64, 64)));

      ImageFetch fetch = fetch("/a.png", URI.create("https://gone.example/p"));

      assertThat(fetch).isInstanceOf(ImageFetch.Unavailable.class);
    }
  }

  static Stream<URI> internalBlogs() {
    return Stream.of(PRIVATE_POST, NAMED_LOOPBACK_POST);
  }

  static Stream<URI> routableBlogs() {
    return Stream.of(PUBLIC_POST, PRIVATE_POST, NAMED_LOOPBACK_POST);
  }

  @Nested
  class Redirects {

    @Test
    @DisplayName("AC-SRCH-78: a redirect to an http URL is rejected")
    void a_redirect_to_http_is_rejected() {
      redirect("/a.png", "http://localhost:" + server.getHttpsPort() + "/b.png");

      assertRejected(fetch("/a.png", LOOPBACK_POST), ImageRejection.NOT_HTTPS);
    }

    @Test
    @DisplayName("AC-SRCH-78: three redirects are followed")
    void three_redirects_end_in_fetched() {
      redirect("/r1", url("/r2").toString());
      redirect("/r2", url("/r3").toString());
      redirect("/r3", url("/final.png").toString());
      serve("/final.png", image("image/png", TestImages.png(64, 64)));

      assertThat(fetch("/r1", LOOPBACK_POST)).isInstanceOf(ImageFetch.Fetched.class);
    }

    @Test
    @DisplayName("AC-SRCH-78: a fourth redirect is TOO_MANY_REDIRECTS")
    void a_fourth_redirect_is_rejected() {
      redirect("/s1", url("/s2").toString());
      redirect("/s2", url("/s3").toString());
      redirect("/s3", url("/s4").toString());
      redirect("/s4", url("/final.png").toString());
      serve("/final.png", image("image/png", TestImages.png(64, 64)));

      assertRejected(fetch("/s1", LOOPBACK_POST), ImageRejection.TOO_MANY_REDIRECTS);
      server.verify(0, getRequestedFor(urlEqualTo("/final.png")));
    }
  }

  static Stream<Arguments> refusedResponses() {
    byte[] png = TestImages.png(64, 64);
    byte[] overCap = TestImages.paddedTo(png, MAX_BYTES + 1);
    return Stream.of(
        Arguments.of(
            "declared over 10 MiB",
            image("image/png", overCap).withHeader("Content-Length", String.valueOf(MAX_BYTES + 1)),
            ImageRejection.TOO_LARGE),
        Arguments.of(
            "streamed over 10 MiB with no length",
            image("image/png", overCap),
            ImageRejection.TOO_LARGE),
        Arguments.of("WebP", image("image/webp", png), ImageRejection.UNSUPPORTED_TYPE),
        Arguments.of("HTML", image("text/html", png), ImageRejection.UNSUPPORTED_TYPE),
        Arguments.of(
            "no content type",
            aResponse().withStatus(200).withBody(png),
            ImageRejection.UNSUPPORTED_TYPE),
        Arguments.of(
            "JPEG that is not an image",
            image("image/jpeg", "not an image".getBytes(UTF_8)),
            ImageRejection.UNDECODABLE),
        Arguments.of(
            "PNG declaring 4,097 x 4,096 pixels",
            image("image/png", TestImages.pngDeclaring(4097, 4096)),
            ImageRejection.TOO_LARGE),
        Arguments.of(
            "63 x 200 PNG", image("image/png", TestImages.png(63, 200)), ImageRejection.TOO_SMALL),
        Arguments.of(
            "200 x 63 PNG", image("image/png", TestImages.png(200, 63)), ImageRejection.TOO_SMALL),
        Arguments.of(
            "1 x 1 PNG", image("image/png", TestImages.png(1, 1)), ImageRejection.TOO_SMALL));
  }

  @Nested
  class Limits {

    @ParameterizedTest(name = "{0}")
    @MethodSource("nz.sounie.blogmcp.search.adapter.out.HttpImageSourceTest#refusedResponses")
    @DisplayName(
        "AC-SRCH-79: size, type and pixel limits reject the image, with one stderr line naming the"
            + " URL and the reason")
    void limits_reject_the_image(
        String kind, ResponseDefinitionBuilder response, ImageRejection reason) {
      serve("/image", response);

      ImageFetch fetch = fetch("/image", LOOPBACK_POST);

      assertRejected(fetch, reason);
      assertThat(stderrLines())
          .singleElement()
          .asString()
          .contains(url("/image").toString())
          .contains(reason.name());
    }

    /**
     * Cutting a body short cancels the exchange, and the JDK client may then fail it ("Stream N
     * cancelled" over HTTP/2) instead of delivering the empty body; how often depends on thread
     * timing. Many concurrent fetches (each with its own client, as in production) make that timing
     * vary enough for the failure to show up when it is not handled.
     */
    @Test
    @DisplayName(
        "AC-SRCH-79: a body refused at its headers is rejected even when the client fails the"
            + " cancelled exchange")
    void a_body_cut_short_is_rejected_whatever_the_client_does_after_the_cancel() throws Exception {
      serve("/page", image("text/html", TestImages.png(64, 64)));
      ExecutorService threads = Executors.newFixedThreadPool(16);
      try {
        List<Future<ImageFetch>> fetches =
            IntStream.range(0, 400)
                .mapToObj(i -> threads.submit(() -> fetch("/page", LOOPBACK_POST)))
                .toList();
        for (Future<ImageFetch> fetch : fetches) {
          assertRejected(fetch.get(), ImageRejection.UNSUPPORTED_TYPE);
        }
      } finally {
        threads.shutdownNow();
      }
    }

    @ParameterizedTest(name = "length declared: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("AC-SRCH-79: a body of exactly 10 MiB is read")
    void a_body_of_exactly_the_cap_is_read(boolean declared) {
      byte[] atCap = TestImages.paddedTo(TestImages.png(64, 64), MAX_BYTES);
      ResponseDefinitionBuilder response = image("image/png", atCap);
      serve(
          "/big.png",
          declared ? response.withHeader("Content-Length", String.valueOf(MAX_BYTES)) : response);

      ImagePixels pixels = fetched(fetch("/big.png", LOOPBACK_POST));

      assertThat(pixels.width()).isEqualTo(64);
    }

    @Test
    @DisplayName("AC-SRCH-79: a 64 x 64 PNG is fetched")
    void the_smallest_accepted_image_is_fetched() {
      serve("/a.png", image("image/png; charset=binary", TestImages.png(64, 64)));

      assertThat(fetched(fetch("/a.png", LOOPBACK_POST)).height()).isEqualTo(64);
    }
  }

  @Nested
  class Failures {

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 429, 408})
    @DisplayName("AC-SRCH-80: a transient server status is unavailable, naming the status")
    void transient_status_is_unavailable(int status) {
      serve("/a.png", aResponse().withStatus(status));

      ImageFetch fetch = fetch("/a.png", LOOPBACK_POST);

      assertThat(fetch)
          .isInstanceOfSatisfying(
              ImageFetch.Unavailable.class,
              unavailable -> assertThat(unavailable.detail()).contains(String.valueOf(status)));
    }

    @Test
    @DisplayName("AC-SRCH-80: no answer within the response timeout is unavailable")
    void a_timeout_is_unavailable() {
      serve(
          "/slow.png",
          image("image/png", TestImages.png(64, 64))
              .withFixedDelay((int) RESPONSE_TIMEOUT.multipliedBy(3).toMillis()));

      ImageFetch fetch = fetch("/slow.png", LOOPBACK_POST);

      assertThat(fetch)
          .isInstanceOfSatisfying(
              ImageFetch.Unavailable.class,
              unavailable -> assertThat(unavailable.detail()).isNotBlank());
    }

    @Test
    @DisplayName(
        "AC-SRCH-80: prompt headers with a body dripping past the response timeout is unavailable,"
            + " without waiting for the body")
    void a_dripping_body_is_unavailable_at_the_deadline() {
      serve(
          "/drip.png",
          image("image/png", TestImages.png(64, 64))
              .withChunkedDribbleDelay(20, (int) RESPONSE_TIMEOUT.multipliedBy(4).toMillis()));
      long start = System.nanoTime();

      ImageFetch fetch = fetch("/drip.png", LOOPBACK_POST);

      assertThat(fetch)
          .isInstanceOfSatisfying(
              ImageFetch.Unavailable.class,
              unavailable -> assertThat(unavailable.detail()).isNotBlank());
      assertThat(Duration.ofNanos(System.nanoTime() - start))
          .isLessThan(RESPONSE_TIMEOUT.multipliedBy(2));
    }

    @Test
    @DisplayName("AC-SRCH-80: a refused connection is unavailable")
    void a_refused_connection_is_unavailable() throws IOException {
      int closedPort;
      try (ServerSocket socket = new ServerSocket(0)) {
        closedPort = socket.getLocalPort();
      }

      ImageFetch fetch =
          source(resolver())
              .fetch(URI.create("https://localhost:" + closedPort + "/a.png"), LOOPBACK_POST);

      assertThat(fetch)
          .isInstanceOfSatisfying(
              ImageFetch.Unavailable.class,
              unavailable -> assertThat(unavailable.detail()).isNotBlank());
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 403, 410})
    @DisplayName("AC-SRCH-80: a permanent client error is rejected")
    void permanent_status_is_rejected(int status) {
      serve("/a.png", aResponse().withStatus(status));

      assertRejected(fetch("/a.png", LOOPBACK_POST), ImageRejection.HTTP_STATUS);
    }
  }
}
