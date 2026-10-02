package nz.sounie.blogmcp.search.domain;

import static nz.sounie.blogmcp.search.domain.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each variant's apply, against the real in-memory index and a fake embedder. */
class IndexDecisionTest {

  private final InMemoryVectorIndex index = new InMemoryVectorIndex();
  private final FakeEmbedder embedder = new FakeEmbedder();
  private final PostIndexer indexer =
      new PostIndexer(
          ChunkingPolicy.standard(),
          PassageComposition.standard(),
          FakeTokenCounter.perWord(1),
          embedder);
  private final IndexWork work = new IndexWork(index, indexer);

  private final PostToIndex post = aPost().id("sounie-wp:1").words(700).build();
  private final IndexedPost existing =
      indexed(post, fingerprint('z'), Vectors.axis(1), Vectors.axis(2), Vectors.axis(3));

  @Test
  void for_absent_is_add_and_remove_is_remove() {
    assertThat(IndexDecision.forAbsent(post)).isEqualTo(new IndexDecision.Add(post));
    assertThat(IndexDecision.remove(post.id())).isEqualTo(new IndexDecision.Remove(post.id()));
    assertThat(new IndexDecision.Add(post).postId()).isEqualTo(post.id());
    assertThat(new IndexDecision.ReEmbed(post).postId()).isEqualTo(post.id());
    assertThat(new IndexDecision.RefreshMetadata(existing, post.metadata()).postId())
        .isEqualTo(post.id());
  }

  @Test
  @DisplayName("AC-SRCH-11: Add indexes and saves the post")
  void add_saves_the_indexed_post() {
    IndexOutcome outcome = new IndexDecision.Add(post).apply(work);

    assertThat(outcome).isEqualTo(IndexOutcome.ADDED);
    assertThat(index.find(post.id())).hasValueSatisfying(p -> assertThat(p.chunks()).hasSize(3));
    assertThat(embedder.passageCallCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-SRCH-13: ReEmbed replaces the entry as a whole")
  void re_embed_replaces_the_entry() {
    index.save(existing);
    PostToIndex revised = aPost().id("sounie-wp:1").words(120).build();

    IndexOutcome outcome = new IndexDecision.ReEmbed(revised).apply(work);

    assertThat(outcome).isEqualTo(IndexOutcome.RE_EMBEDDED);
    IndexedPost saved = index.find(post.id()).orElseThrow();
    assertThat(saved.chunks()).hasSize(1);
    assertThat(saved.fingerprint()).isEqualTo(indexer.fingerprintOf(revised));
  }

  @Test
  @DisplayName("AC-SRCH-17: a failing embedder leaves the old entry in place (embed before mutate)")
  void re_embed_failure_keeps_the_old_entry() {
    index.save(existing);
    embedder.failing();

    assertThatThrownBy(() -> new IndexDecision.ReEmbed(post).apply(work))
        .isInstanceOf(EmbedderUnavailable.class);
    assertThat(index.find(post.id())).containsSame(existing);
  }

  @Test
  @DisplayName("AC-SRCH-14: RefreshMetadata saves new metadata without embedding")
  void refresh_metadata_saves_without_embedding() {
    index.save(existing);
    PostMetadata newer = aPost().id("sounie-wp:1").tags("Kotlin").metadata();

    IndexOutcome outcome = new IndexDecision.RefreshMetadata(existing, newer).apply(work);

    assertThat(outcome).isEqualTo(IndexOutcome.METADATA_REFRESHED);
    IndexedPost saved = index.find(post.id()).orElseThrow();
    assertThat(saved.metadata()).isEqualTo(newer);
    assertThat(saved.chunks()).isEqualTo(existing.chunks());
    assertThat(saved.fingerprint()).isEqualTo(existing.fingerprint());
    assertThat(embedder.passageCallCount()).isZero();
  }

  @Test
  @DisplayName("AC-SRCH-12: Keep changes nothing")
  void keep_changes_nothing() {
    index.save(existing);

    IndexOutcome outcome = new IndexDecision.Keep(post.id()).apply(work);

    assertThat(outcome).isEqualTo(IndexOutcome.UNCHANGED);
    assertThat(index.find(post.id())).containsSame(existing);
    assertThat(embedder.passageCallCount()).isZero();
  }

  @Test
  @DisplayName("AC-SRCH-35: Exclude removes an existing entry without embedding")
  void exclude_removes_an_existing_entry() {
    index.save(existing);

    IndexOutcome outcome = new IndexDecision.Exclude(post.id()).apply(work);

    assertThat(outcome).isEqualTo(IndexOutcome.REMOVED);
    assertThat(index.find(post.id())).isEmpty();
    assertThat(embedder.passageCallCount()).isZero();
  }

  @Test
  @DisplayName("AC-SRCH-33: Exclude with no entry is EXCLUDED")
  void exclude_without_entry_is_excluded() {
    assertThat(new IndexDecision.Exclude(post.id()).apply(work)).isEqualTo(IndexOutcome.EXCLUDED);
    assertThat(index.ids()).isEmpty();
    assertThat(embedder.passageCallCount()).isZero();
  }

  @Test
  @DisplayName("AC-SRCH-16: Remove deletes the entry")
  void remove_deletes_the_entry() {
    index.save(existing);

    assertThat(new IndexDecision.Remove(post.id()).apply(work)).isEqualTo(IndexOutcome.REMOVED);
    assertThat(index.find(post.id())).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-16: Remove with no entry is ALREADY_ABSENT")
  void remove_without_entry_is_already_absent() {
    assertThat(new IndexDecision.Remove(post.id()).apply(work))
        .isEqualTo(IndexOutcome.ALREADY_ABSENT);
  }

  @Test
  @DisplayName("AC-SRCH-38: Unreadable is FAILED and leaves an existing entry alone")
  void unreadable_fails_and_keeps_the_entry() {
    index.save(existing);

    IndexOutcome outcome = new IndexDecision.Unreadable(post.id(), "title is missing").apply(work);

    assertThat(outcome).isEqualTo(IndexOutcome.FAILED);
    assertThat(index.find(post.id())).containsSame(existing);
    assertThat(embedder.passageCallCount()).isZero();
  }

  @Test
  @DisplayName("AC-SRCH-38: Unreadable with no entry is FAILED and adds nothing")
  void unreadable_without_entry_adds_nothing() {
    assertThat(new IndexDecision.Unreadable(post.id(), "bad URL").apply(work))
        .isEqualTo(IndexOutcome.FAILED);
    assertThat(index.ids()).isEmpty();
  }
}
