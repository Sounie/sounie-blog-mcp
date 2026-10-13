package nz.sounie.blogmcp.catalog.domain.post;

import java.net.URI;

/**
 * One image that is part of a post's content: an absolute {@code https} URL with a host, plus plain
 * alt text and caption, each stripped and empty when absent (never null).
 */
public record PostImage(URI url, String altText, String caption) {

  public PostImage {
    // TODO(green): validate the URL (absolute https with a host) and strip alt text and caption.
  }
}
