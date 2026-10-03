package nz.sounie.blogmcp.catalog.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import nz.sounie.blogmcp.catalog.adapter.out.InMemoryPostRepository;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** app.md 3.7: telling a URL from a post ID is catalog's rule, tested here once. */
class PostReferenceTest {

  private static PostId postId(String site, String source) {
    return new PostId(new SiteId(site), new SourcePostId(source));
  }

  @Test
  void text_without_a_scheme_is_a_post_id() {
    assertThat(PostReference.parse("sounie-wp:123"))
        .isEqualTo(new PostReference.ById(postId("sounie-wp", "123")));
  }

  @Test
  void surrounding_white_space_is_ignored() {
    assertThat(PostReference.parse("  sounie-wp:123\n"))
        .isEqualTo(new PostReference.ById(postId("sounie-wp", "123")));
  }

  @Test
  void a_source_post_id_may_itself_contain_colons() {
    assertThat(PostReference.parse("elegant:tag:blogger.com,1999:post-9"))
        .isEqualTo(new PostReference.ById(postId("elegant", "tag:blogger.com,1999:post-9")));
  }

  @Test
  void a_colon_without_slashes_is_a_well_formed_post_id_not_a_url() {
    assertThat(PostReference.parse("mailto:a@b"))
        .isEqualTo(new PostReference.ById(postId("mailto", "a@b")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://blog2.sounie.nz/2026/09/20/hello/",
        "http://BLOG2.sounie.nz/2026/09/20/hello#comments"
      })
  void text_starting_with_a_scheme_and_slashes_is_a_url(String url) {
    assertThat(PostReference.parse(url))
        .isEqualTo(new PostReference.ByUrl(new WebAddress(URI.create(url))));
  }

  @Test
  void a_url_is_trimmed_too() {
    assertThat(PostReference.parse(" https://blog2.sounie.nz/hello/ "))
        .isEqualTo(
            new PostReference.ByUrl(new WebAddress(URI.create("https://blog2.sounie.nz/hello/"))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void blank_text_is_invalid(String text) {
    assertThatThrownBy(() -> PostReference.parse(text)).isInstanceOf(InvalidPostReference.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-an-id", // no colon
        "Sounie_WP:1", // site part is not a site ID
        "sounie-wp:", // blank source post ID
        "sounie-wp:   "
      })
  void text_that_is_not_a_well_formed_post_id_is_invalid(String text) {
    assertThatThrownBy(() -> PostReference.parse(text))
        .isInstanceOf(InvalidPostReference.class)
        .hasMessageContaining(text.strip());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://blog2.sounie.nz/hello/", "https://", "https://exa mple.com/"})
  void text_with_a_scheme_that_is_not_an_absolute_http_url_is_invalid(String text) {
    assertThatThrownBy(() -> PostReference.parse(text)).isInstanceOf(InvalidPostReference.class);
  }

  @Test
  void a_post_id_reference_finds_the_stored_post() {
    PostRepository posts = repositoryHolding("https://blog2.sounie.nz/2026/09/20/hello/");

    assertThat(PostReference.parse("sounie-wp:123").lookUpIn(posts))
        .hasValueSatisfying(post -> assertThat(post.id()).isEqualTo(postId("sounie-wp", "123")));
  }

  @Test
  void a_url_reference_finds_the_stored_post_by_its_canonical_url() {
    PostRepository posts = repositoryHolding("https://blog2.sounie.nz/2026/09/20/hello/");

    assertThat(PostReference.parse("http://blog2.sounie.nz/2026/09/20/hello/").lookUpIn(posts))
        .hasValueSatisfying(post -> assertThat(post.id()).isEqualTo(postId("sounie-wp", "123")));
  }

  private static PostRepository repositoryHolding(String url) {
    InMemoryPostRepository posts = new InMemoryPostRepository();
    posts.save(PostSnapshotBuilder.aSnapshot().sourcePostId("123").url(url).buildStoredPost());
    return posts;
  }
}
