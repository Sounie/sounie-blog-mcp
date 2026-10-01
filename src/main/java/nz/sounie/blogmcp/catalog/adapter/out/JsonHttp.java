package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.catalog.domain.SourceUnavailable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * GETs JSON from a blog platform, turning every transport or HTTP failure into {@link
 * SourceUnavailable}.
 */
final class JsonHttp {

  static final String USER_AGENT = "blog-mcp/0.1 (+https://github.com/Sounie/sounie-blog-mcp)";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final int OK = 200;

  /** A parsed JSON response with its headers. */
  record Response(JsonNode body, HttpHeaders headers) {}

  private final HttpClient httpClient;
  private final ObjectMapper json = JsonMapper.builder().build();

  JsonHttp(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /** An HTTP client with sensible timeouts that follows normal redirects. */
  static HttpClient defaultClient() {
    return HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  /**
   * @param base the API base, e.g. the site's base URL
   * @param path path below the base, starting with {@code /}
   * @param query query parameters in order; values are URL-encoded
   */
  Response get(URI base, String path, Map<String, String> query) {
    URI uri = URI.create(withoutTrailingSlash(base.toString()) + path + "?" + encode(query));
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .GET()
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() != OK) {
      throw new SourceUnavailable("HTTP " + response.statusCode() + " from " + uri);
    }
    try {
      return new Response(json.readTree(response.body()), response.headers());
    } catch (JacksonException e) {
      throw new SourceUnavailable("Unreadable JSON from " + uri, e);
    }
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new SourceUnavailable("Could not reach " + request.uri() + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SourceUnavailable("Interrupted while reading " + request.uri(), e);
    }
  }

  private static String withoutTrailingSlash(String base) {
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private static String encode(Map<String, String> query) {
    return query.entrySet().stream()
        .map(
            parameter ->
                URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
