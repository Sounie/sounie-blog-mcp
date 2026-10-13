package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;

/** Test data builder for {@link ImageCandidate}s: every attribute absent unless set. */
final class ImageCandidateBuilder {

  static final CanonicalUrl POST_URL =
      new CanonicalUrl(URI.create("https://blog2.sounie.nz/2026/09/20/hello/"));

  private String src = "";
  private String srcset = "";
  private String classNames = "";
  private String width = "";
  private String height = "";

  private ImageCandidateBuilder() {}

  static ImageCandidateBuilder anImage() {
    return new ImageCandidateBuilder().src("https://blog2.sounie.nz/wp-content/uploads/swan.jpg");
  }

  ImageCandidateBuilder src(String src) {
    this.src = src;
    return this;
  }

  ImageCandidateBuilder srcset(String srcset) {
    this.srcset = srcset;
    return this;
  }

  ImageCandidateBuilder classNames(String classNames) {
    this.classNames = classNames;
    return this;
  }

  ImageCandidateBuilder width(String width) {
    this.width = width;
    return this;
  }

  ImageCandidateBuilder height(String height) {
    this.height = height;
    return this;
  }

  ImageCandidate build() {
    return new ImageCandidate(POST_URL, src, srcset, classNames, width, height, "", "");
  }
}
