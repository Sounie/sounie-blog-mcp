package nz.sounie.blogmcp.catalog.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.PostRepository;

/** Looks up one post by post ID or by URL. */
public final class GetPost {

  private static final int DEFAULT_HTTP_PORT = 80;

  private final PostRepository posts;

  public GetPost(PostRepository posts) {
    this.posts = posts;
  }

  /**
   * @param postId external form {@code <siteId>:<sourcePostId>}
   * @throws InvalidPostReference if the text is not a post ID
   */
  public Optional<PostView> byId(String postId) {
    return posts.findById(parsePostId(postId)).map(PostView::of);
  }

  /**
   * Normalises the URL before looking it up: lower-case scheme and host, {@code http} treated as
   * {@code https}, no fragment, no default port, trailing slash ignored, query kept.
   *
   * @throws InvalidPostReference if the text is not an absolute http(s) URL
   */
  public Optional<PostView> byUrl(String url) {
    return posts.findByCanonicalUrl(asHttps(parseWebUrl(url))).map(PostView::of);
  }

  private static PostId parsePostId(String text) {
    try {
      return PostId.parse(text);
    } catch (IllegalArgumentException e) {
      throw new InvalidPostReference("Not a post ID (<siteId>:<sourcePostId>): '" + text + "'");
    }
  }

  private static URI parseWebUrl(String text) {
    URI uri;
    try {
      uri = new URI(text);
    } catch (URISyntaxException e) {
      throw notAUrl(text);
    }
    String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
    if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
      throw notAUrl(text);
    }
    return uri;
  }

  /** The same address as https, so it can be compared with canonical URLs. */
  private static CanonicalUrl asHttps(URI uri) {
    boolean defaultHttpPort =
        "http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == DEFAULT_HTTP_PORT;
    String port = uri.getPort() == -1 || defaultHttpPort ? "" : ":" + uri.getPort();
    String path = Optional.ofNullable(uri.getRawPath()).orElse("");
    String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
    return new CanonicalUrl(URI.create("https://" + uri.getHost() + port + path + query));
  }

  private static InvalidPostReference notAUrl(String text) {
    return new InvalidPostReference("Not an absolute http(s) URL: '" + text + "'");
  }
}
