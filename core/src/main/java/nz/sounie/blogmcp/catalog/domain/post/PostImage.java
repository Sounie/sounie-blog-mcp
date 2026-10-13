package nz.sounie.blogmcp.catalog.domain.post;

import java.net.URI;
import java.util.Objects;

/**
 * One image that is part of a post's content: an absolute {@code https} URL with a host, plus plain
 * alt text and caption, each stripped and empty when absent (never null).
 */
public record PostImage(URI url, String altText, String caption) {

  public PostImage {
    Objects.requireNonNull(url, "url");
    if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
      throw new IllegalArgumentException("Post image URL must be absolute https: " + url);
    }
    altText = Objects.requireNonNull(altText, "altText").strip();
    caption = Objects.requireNonNull(caption, "caption").strip();
  }
}
