package nz.sounie.blogmcp.search.domain;

import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostIndexerTest {

  private final FakeEmbedder embedder = new FakeEmbedder("fake-model");
  private final TokenCounter tokens = FakeTokenCounter.perWord(1);
  private final PostIndexer indexer =
      new PostIndexer(ChunkingPolicy.standard(), PassageComposition.standard(), tokens, embedder);

  private final PostToIndex post =
      aPost().id("sounie-wp:1").title("Records in Java 25").words(700).tags("Java").build();

  @Test
  @DisplayName("AC-SRCH-29: the recipe is made from the model ID, chunking and composition")
  void recipe_combines_model_chunking_and_composition() {
    assertThat(indexer.recipe())
        .isEqualTo(
            IndexRecipe.of("fake-model", ChunkingPolicy.standard(), PassageComposition.standard()));
  }

  @Test
  void fingerprint_is_computed_under_the_current_recipe() {
    assertThat(indexer.fingerprintOf(post))
        .isEqualTo(
            ContentFingerprint.of(indexer.recipe(), post.title(), WordSequence.of(post.body())));
  }

  @Test
  @DisplayName("AC-SRCH-11: a 700-word post is indexed as 3 chunks with metadata and fingerprint")
  void indexes_chunks_metadata_and_fingerprint() {
    IndexedPost indexed = indexer.index(post);

    assertThat(indexed.id()).isEqualTo(post.id());
    assertThat(indexed.metadata()).isEqualTo(post.metadata());
    assertThat(indexed.fingerprint()).isEqualTo(indexer.fingerprintOf(post));
    assertThat(indexed.chunks()).extracting(IndexedChunk::index).containsExactly(0, 1, 2);
    assertThat(indexed.chunks())
        .extracting(IndexedChunk::text)
        .containsExactly(
            String.join(" ", Words.range(1, 300)),
            String.join(" ", Words.range(251, 550)),
            String.join(" ", Words.range(501, 700)));
  }

  @Test
  @DisplayName("AC-SRCH-7: all of a post's passages are embedded in one call, title first")
  void embeds_all_passages_in_one_call() {
    indexer.index(post);

    assertThat(embedder.passageCalls()).hasSize(1);
    assertThat(embedder.passageCalls().getFirst())
        .extracting(Passage::text)
        .hasSize(3)
        .allSatisfy(text -> assertThat(text).startsWith("Records in Java 25\n"));
  }

  @Test
  void each_chunk_gets_the_embedding_of_its_passage() {
    Embedding chosen = Vectors.axis(7);
    embedder.assign("Records in Java 25\n" + String.join(" ", Words.range(251, 550)), chosen);

    IndexedPost indexed = indexer.index(post);

    assertThat(indexed.chunks().get(1).embedding()).isSameAs(chosen);
  }

  @Test
  @DisplayName("AC-SRCH-32: no passage carries the query instruction")
  void passages_never_carry_the_query_instruction() {
    indexer.index(post);

    assertThat(embedder.allPassages())
        .extracting(Passage::text)
        .noneMatch(text -> text.startsWith(QueryPassage.INSTRUCTION));
  }

  @Test
  @DisplayName("AC-SRCH-4: an empty body is one chunk with empty text, embedded as the title")
  void empty_body_is_indexed_by_title() {
    IndexedPost indexed = indexer.index(aPost().title("Hello").body("  \n").build());

    assertThat(indexed.chunks()).extracting(IndexedChunk::text).containsExactly("");
    assertThat(embedder.allPassages()).containsExactly(new Passage("Hello"));
  }

  @Test
  @DisplayName("AC-SRCH-4: a post with blank title and body has zero chunks")
  void blank_title_and_body_give_zero_chunks() {
    IndexedPost indexed = indexer.index(aPost().title(" ").body("").build());

    assertThat(indexed.chunks()).isEqualTo(List.of());
  }

  @Test
  @DisplayName("S3: too few embeddings for the chunks is EmbedderUnavailable")
  void too_few_embeddings_is_embedder_unavailable() {
    embedder.returningWrongCount(-1);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> indexer.index(post))
        .isInstanceOf(EmbedderUnavailable.class);
  }

  @Test
  @DisplayName("S3: too many embeddings for the chunks is EmbedderUnavailable")
  void too_many_embeddings_is_embedder_unavailable() {
    embedder.returningWrongCount(1);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> indexer.index(post))
        .isInstanceOf(EmbedderUnavailable.class);
  }
}
