package nz.sounie.blogmcp.catalog.domain.post;

import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.image;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Section 3.6: order and duplicates of a post's images. */
class PostImagesTest {

  private static final PostImage A = image("https://x.example/a.jpg", "First", "");
  private static final PostImage B = image("https://x.example/b.jpg");
  private static final PostImage C = image("https://x.example/c.jpg");

  @Test
  void keeps_the_images_in_document_order() {
    assertThat(PostImages.of(List.of(C, A, B)).asList()).containsExactly(C, A, B);
  }

  @Test
  @DisplayName("AC-CAT-37: a later image with the same URL is dropped; the first one wins")
  void records_each_url_once_keeping_the_first_occurrence() {
    PostImage againWithOtherAlt = image("https://x.example/a.jpg", "Second", "A caption");

    PostImages images = PostImages.of(List.of(A, B, againWithOtherAlt));

    assertThat(images.asList()).containsExactly(A, B);
  }

  @Test
  @DisplayName("Modeller decision: size variants of one picture are different URLs, both kept")
  void different_urls_of_the_same_picture_are_both_kept() {
    PostImage sized = image("https://x.example/a.jpg?w=300");

    assertThat(PostImages.of(List.of(A, sized)).asList()).containsExactly(A, sized);
  }

  @Test
  void none_is_the_empty_list_and_equals_an_empty_of() {
    assertThat(PostImages.none().asList()).isEmpty();
    assertThat(PostImages.of(List.of())).isEqualTo(PostImages.none());
  }

  @Test
  void lists_with_the_same_images_in_the_same_order_are_equal() {
    assertThat(PostImages.of(List.of(A, B)))
        .isEqualTo(PostImages.of(List.of(A, B)))
        .hasSameHashCodeAs(PostImages.of(List.of(A, B)));
  }

  @Test
  @DisplayName("AC-CAT-39: the same images in a different order are a different list")
  void lists_in_a_different_order_are_not_equal() {
    assertThat(PostImages.of(List.of(A, B))).isNotEqualTo(PostImages.of(List.of(B, A)));
  }

  @Test
  void is_unaffected_by_later_changes_to_the_given_list() {
    List<PostImage> given = new ArrayList<>(List.of(A));
    PostImages images = PostImages.of(given);

    given.add(B);

    assertThat(images.asList()).containsExactly(A);
    assertThatThrownBy(() -> images.asList().add(C))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
