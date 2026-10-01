package nz.sounie.blogmcp.search.domain;

import static nz.sounie.blogmcp.search.domain.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import org.junit.jupiter.api.Test;

/** Wiring only: each change reaches the decision that owns it. */
class IndexChangeTest {

  private final InMemoryVectorIndex index = new InMemoryVectorIndex();
  private final IndexWork work =
      new IndexWork(
          index,
          new PostIndexer(
              ChunkingPolicy.standard(),
              PassageComposition.standard(),
              FakeTokenCounter.perWord(1),
              new FakeEmbedder()));

  @Test
  void upsert_decides_through_for_post_with_the_current_fingerprint() {
    PostToIndex post = aPost().build();

    assertThat(new IndexChange.Upsert(post).applyTo(work)).isEqualTo(IndexOutcome.ADDED);
    assertThat(new IndexChange.Upsert(post).applyTo(work)).isEqualTo(IndexOutcome.UNCHANGED);
  }

  @Test
  void remove_applies_a_remove_decision() {
    PostToIndex post = aPost().build();
    index.save(indexed(post, fingerprint('a'), Vectors.axis(1)));

    assertThat(new IndexChange.Remove(post.id()).applyTo(work)).isEqualTo(IndexOutcome.REMOVED);
    assertThat(new IndexChange.Remove(post.id()).applyTo(work))
        .isEqualTo(IndexOutcome.ALREADY_ABSENT);
  }
}
