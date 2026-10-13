package nz.sounie.blogmcp.search.domain.image;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.post.MalformedCatalogPost;
import nz.sounie.blogmcp.search.domain.text.WordSequence;

/**
 * Search's own copy of one catalog post image: an absolute {@code https} URL with a host, its alt
 * text and its caption (plain text, empty when absent, never null).
 *
 * <p>The compact constructor throws {@link MalformedCatalogPost} for a null field or a URL that is
 * not absolute {@code https} with a host.
 */
public record PostImage(URI url, String altText, String caption) {

  private static final String HTTPS = "https";

  public PostImage {
    MalformedCatalogPost.requirePresent(url, "image URL");
    MalformedCatalogPost.requirePresent(altText, "image alt text");
    MalformedCatalogPost.requirePresent(caption, "image caption");
    requireHttpsWithHost(url);
  }

  /**
   * Translates a published-language image.
   *
   * @throws MalformedCatalogPost if a field is null or the URL is not absolute {@code https} with a
   *     host
   */
  public static PostImage of(String url, String altText, String caption) {
    return new PostImage(
        parsed(MalformedCatalogPost.requirePresent(url, "image URL")), altText, caption);
  }

  /** The alt text if not blank, else the caption if not blank, else {@code Image: <url>}. */
  public String description() {
    return nonBlank(altText).or(() -> nonBlank(caption)).orElseGet(() -> "Image: " + url);
  }

  private static Optional<String> nonBlank(String text) {
    return Optional.of(WordSequence.collapseWhiteSpace(text)).filter(s -> !s.isEmpty());
  }

  private static URI parsed(String url) {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new MalformedCatalogPost("The image URL is not a URI: '" + url + "'");
    }
  }

  private static void requireHttpsWithHost(URI url) {
    if (!HTTPS.equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
      throw new MalformedCatalogPost("The image URL is not absolute https with a host: " + url);
    }
  }
}
