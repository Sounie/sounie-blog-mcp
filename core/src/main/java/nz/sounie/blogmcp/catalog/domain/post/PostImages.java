package nz.sounie.blogmcp.catalog.domain.post;

import java.util.List;

/**
 * The ordered post images of a post: document order, at most one image per URL (the first
 * occurrence wins). May be empty. Two lists are equal only with the same images in the same order.
 */
public final class PostImages {

  private static final PostImages NONE = new PostImages(List.of());

  private final List<PostImage> images;

  private PostImages(List<PostImage> images) {
    this.images = images;
  }

  /** The empty list. */
  public static PostImages none() {
    return NONE;
  }

  /** The given images in order, dropping any later image whose URL equals an earlier one. */
  public static PostImages of(List<PostImage> images) {
    throw new UnsupportedOperationException("PostImages.of is not implemented yet");
  }

  /** The images, in document order. */
  public List<PostImage> asList() {
    return images;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PostImages that && images.equals(that.images);
  }

  @Override
  public int hashCode() {
    return images.hashCode();
  }

  @Override
  public String toString() {
    return "PostImages" + images;
  }
}
