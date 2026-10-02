package nz.sounie.blogmcp.search.domain.index;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.Embedder;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.text.Chunk;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;
import nz.sounie.blogmcp.search.domain.text.WordSequence;

/**
 * Domain service that creates {@link IndexedPost}s: word sequence, chunking, passage composition,
 * then one {@link Embedder#embedPassages} call for all of the post's passages.
 */
public final class PostIndexer {

  private final ChunkingPolicy chunking;
  private final PassageComposition composition;
  private final TokenCounter tokens;
  private final Embedder embedder;

  public PostIndexer(
      ChunkingPolicy chunking,
      PassageComposition composition,
      TokenCounter tokens,
      Embedder embedder) {
    this.chunking = Objects.requireNonNull(chunking, "chunking");
    this.composition = Objects.requireNonNull(composition, "composition");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.embedder = Objects.requireNonNull(embedder, "embedder");
  }

  /** The current index recipe: model ID, chunking parameters and composition version. */
  public IndexRecipe recipe() {
    return IndexRecipe.of(embedder.modelId(), chunking, composition);
  }

  /** The content fingerprint of the post under the current recipe. */
  public ContentFingerprint fingerprintOf(PostToIndex post) {
    return post.fingerprintUnder(recipe());
  }

  /**
   * @throws EmbedderUnavailable if the embedder fails; nothing has been changed by then
   */
  public IndexedPost index(PostToIndex post) {
    List<Chunk> chunks = chunksOf(post);
    List<Embedding> embeddings = embeddingsFor(post, chunks);
    List<IndexedChunk> indexed =
        IntStream.range(0, chunks.size())
            .mapToObj(i -> new IndexedChunk(i, chunks.get(i).text(), embeddings.get(i)))
            .toList();
    return IndexedPost.restore(post.id(), post.metadata(), fingerprintOf(post), indexed);
  }

  /**
   * One embedding per chunk, in chunk order.
   *
   * @throws EmbedderUnavailable if the embedder fails or breaks its contract of one embedding per
   *     passage
   */
  private List<Embedding> embeddingsFor(PostToIndex post, List<Chunk> chunks) {
    List<Embedding> embeddings =
        embedder.embedPassages(composition.compose(post.title(), chunks, tokens));
    if (embeddings.size() != chunks.size()) {
      throw new EmbedderUnavailable(
          "The embedder returned "
              + embeddings.size()
              + " embeddings for "
              + chunks.size()
              + " passages of "
              + post.id().external());
    }
    return embeddings;
  }

  /** A post whose title and body are both blank has no chunks (search.md 3.4, rule 4). */
  private List<Chunk> chunksOf(PostToIndex post) {
    WordSequence body = WordSequence.of(post.body());
    return WordSequence.of(post.title()).isEmpty() && body.isEmpty()
        ? List.of()
        : chunking.chunk(body, tokens);
  }
}
