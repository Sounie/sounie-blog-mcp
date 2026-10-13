package nz.sounie.blogmcp.catalog.adapter.out;

import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.PostImages;

/**
 * Image extraction with jsoup: reads the post images out of the same content HTML that text
 * extraction reads. Each {@code <img>} becomes an {@link ImageCandidate}; candidates that an {@link
 * ImageExclusion} excludes are dropped silently.
 */
final class PostImageExtractor {

  /** The post images in the HTML, with relative URLs resolved against the post's URL. */
  PostImages extract(String html, CanonicalUrl postUrl) {
    throw new UnsupportedOperationException("PostImageExtractor.extract is not implemented yet");
  }
}
