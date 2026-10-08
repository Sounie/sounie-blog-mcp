package nz.sounie.blogmcp.search.adapter.out;

import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@link VectorIndex} port's contract, run against the in-memory index and the file index, so
 * swapping one for the other is safe.
 */
abstract class VectorIndexContract {

  protected VectorIndex index;

  protected final PostToIndex post = aPost().id("sounie-wp:1").build();
  protected final IndexedPost first = indexed(post, fingerprint('a'), Vectors.axis(384, 1));
  protected final IndexedPost second =
      indexed(post, fingerprint('b'), Vectors.axis(384, 2), Vectors.axis(384, 3));

  /** A fresh, empty index. */
  protected abstract VectorIndex newIndex();

  @BeforeEach
  void createIndex() {
    index = newIndex();
  }

  /** Compares field by field: {@link IndexedPost} has identity equality. */
  protected static void assertSameEntry(Optional<IndexedPost> actual, IndexedPost expected) {
    assertThat(actual).isPresent();
    assertThat(actual.orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void finds_nothing_when_empty() {
    assertThat(index.find(post.id())).isEmpty();
    assertThat(index.ids()).isEmpty();
    assertThat(index.all()).isEmpty();
  }

  @Test
  void saves_and_finds_a_post() {
    index.save(first);

    assertSameEntry(index.find(post.id()), first);
    assertThat(index.ids()).containsExactly(post.id());
    assertThat(index.all().toList()).singleElement().usingRecursiveComparison().isEqualTo(first);
  }

  @Test
  void save_replaces_the_whole_entry() {
    index.save(first);
    index.save(second);

    assertSameEntry(index.find(post.id()), second);
    assertThat(index.all().toList()).singleElement().usingRecursiveComparison().isEqualTo(second);
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

  @Test
  void all_is_a_snapshot() {
    index.save(first);
    var all = index.all();

    index.remove(post.id());

    assertThat(all).hasSize(1);
  }
}
