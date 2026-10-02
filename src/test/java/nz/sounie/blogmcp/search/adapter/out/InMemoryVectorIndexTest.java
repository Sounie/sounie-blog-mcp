package nz.sounie.blogmcp.search.adapter.out;

import static nz.sounie.blogmcp.search.domain.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.domain.IndexedPost;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.search.domain.Vectors;
import org.junit.jupiter.api.Test;

class InMemoryVectorIndexTest {

  private final InMemoryVectorIndex index = new InMemoryVectorIndex();
  private final PostToIndex post = aPost().id("sounie-wp:1").build();
  private final IndexedPost first = indexed(post, fingerprint('a'), Vectors.axis(1));
  private final IndexedPost second =
      indexed(post, fingerprint('b'), Vectors.axis(2), Vectors.axis(3));

  @Test
  void finds_nothing_when_empty() {
    assertThat(index.find(post.id())).isEmpty();
    assertThat(index.ids()).isEmpty();
    assertThat(index.all()).isEmpty();
  }

  @Test
  void saves_and_finds_a_post() {
    index.save(first);

    assertThat(index.find(post.id())).containsSame(first);
    assertThat(index.ids()).containsExactly(post.id());
    assertThat(index.all()).containsExactly(first);
  }

  @Test
  void save_replaces_the_whole_entry() {
    index.save(first);
    index.save(second);

    assertThat(index.find(post.id())).containsSame(second);
    assertThat(index.all()).containsExactly(second);
  }

  @Test
  void remove_reports_whether_an_entry_was_removed() {
    index.save(first);

    assertThat(index.remove(post.id())).isTrue();
    assertThat(index.find(post.id())).isEmpty();
    assertThat(index.remove(post.id())).isFalse();
  }

  @Test
  void ids_is_a_snapshot() {
    index.save(first);
    var ids = index.ids();

    index.remove(post.id());

    assertThat(ids).containsExactly(post.id());
  }
}
