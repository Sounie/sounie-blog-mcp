package nz.sounie.blogmcp.search.domain.image;

import java.net.URI;

/**
 * Port: fetches one post image's pixels. The post's canonical URL decides which image addresses are
 * permitted; that is the adapter's concern, not the domain's.
 */
public interface ImageSource {

  /** Never throws for an expected outcome: rejections and transient failures are values. */
  ImageFetch fetch(URI imageUrl, URI postUrl);
}
