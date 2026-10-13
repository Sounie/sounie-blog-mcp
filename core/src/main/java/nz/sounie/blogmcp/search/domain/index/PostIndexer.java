package nz.sounie.blogmcp.search.domain.index;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.PassageEmbedder;
import nz.sounie.blogmcp.search.domain.image.PostImage;
import nz.sounie.blogmcp.search.domain.text.Chunk;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.search.domain.text.PassageFormatter;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;
import nz.sounie.blogmcp.search.domain.text.WordSequence;

/**
 * Domain service that creates {@link IndexedPost}s: word sequence, chunking, passage composition,
 * one {@link PassageEmbedder#embedPassages} call for all of the post's passages, then the post's
 * images through its {@link ImageIndexing} strategy (text-only unless {@link #withImageIndexing}).
 */
public final class PostIndexer {

  private final ChunkingPolicy chunking;
  private final PassageComposition composition;
  private final TokenCounter tokens;
  private final PassageEmbedder embedder;
  private final PassageFormatter formatter;
  private final IndexRecipe textRecipe;
  private final ImageIndexing imageIndexing;
  private final IndexRecipe recipe;

  public PostIndexer(
      ChunkingPolicy chunking,
      PassageComposition composition,
      TokenCounter tokens,
      PassageEmbedder embedder) {
    this(chunking, composition, tokens, embedder, PassageFormatter.standard());
  }

  public PostIndexer(
      ChunkingPolicy chunking,
      PassageComposition composition,
      TokenCounter tokens,
      PassageEmbedder embedder,
      PassageFormatter formatter) {
    this(
        chunking,
        composition,
        tokens,
        embedder,
        formatter,
        IndexRecipe.of(modelIdOf(embedder), chunking, composition),
        ImageIndexing.textOnly());
  }

  public PostIndexer(
      ChunkingPolicy chunking,
      PassageComposition composition,
      TokenCounter tokens,
      PassageEmbedder embedder,
      PassageFormatter formatter,
      String preparationVersion,
      PassageTokenBudget budget) {
    this(
        chunking,
        composition,
        tokens,
        embedder,
        formatter,
        IndexRecipe.of(
            modelIdOf(embedder),
            chunking,
            composition,
            Objects.requireNonNull(preparationVersion, "preparationVersion"),
            Objects.requireNonNull(budget, "budget")),
        ImageIndexing.textOnly());
  }

  private PostIndexer(
      ChunkingPolicy chunking,
      PassageComposition composition,
      TokenCounter tokens,
      PassageEmbedder embedder,
      PassageFormatter formatter,
      IndexRecipe textRecipe,
      ImageIndexing imageIndexing) {
    this.chunking = Objects.requireNonNull(chunking, "chunking");
    this.composition = Objects.requireNonNull(composition, "composition");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.embedder = Objects.requireNonNull(embedder, "embedder");
    this.formatter = Objects.requireNonNull(formatter, "formatter");
    this.textRecipe = textRecipe;
    this.imageIndexing = Objects.requireNonNull(imageIndexing, "imageIndexing");
    this.recipe = imageIndexing.appliedTo(textRecipe);
  }

  private static String modelIdOf(PassageEmbedder embedder) {
    return Objects.requireNonNull(embedder, "embedder").modelId();
  }

  /** The same indexer, with this strategy for the post's images. */
  public PostIndexer withImageIndexing(ImageIndexing imageIndexing) {
    return new PostIndexer(
        chunking, composition, tokens, embedder, formatter, textRecipe, imageIndexing);
  }

  /**
   * The current index recipe: model ID, chunking parameters, composition version and, with image
   * indexing, the image part.
   */
  public IndexRecipe recipe() {
    return recipe;
  }

  /** The content fingerprint of the post's current content under the current recipe. */
  public ContentFingerprint fingerprintOf(PostToIndex post) {
    return fingerprintOver(post, imageIndexing.fingerprinted(post));
  }

  /**
   * Embeds the text first, so a text failure changes nothing and fetches no image. The stored
   * fingerprint covers only the settled images, so a pending image makes the entry stale.
   *
   * @throws EmbedderUnavailable if the passage embedder fails; nothing has been changed by then
   */
  public IndexedPost index(PostToIndex post) {
    List<IndexedChunk> chunks = indexedChunks(post);
    List<ImageAttempt> attempts = imageIndexing.attempt(post);
    List<PostImage> settled = attempts.stream().flatMap(a -> a.settled().stream()).toList();
    List<IndexedImage> images = attempts.stream().flatMap(a -> a.entry().stream()).toList();
    return IndexedPost.restore(
        post.id(), post.metadata(), fingerprintOver(post, settled), chunks, images);
  }

  private ContentFingerprint fingerprintOver(PostToIndex post, List<PostImage> images) {
    return ContentFingerprint.of(recipe, post.title(), WordSequence.of(post.body()), images);
  }

  private List<IndexedChunk> indexedChunks(PostToIndex post) {
    List<Chunk> chunks = chunksOf(post);
    List<Embedding> embeddings = embeddingsFor(post, chunks);
    return IntStream.range(0, chunks.size())
        .mapToObj(i -> new IndexedChunk(i, chunks.get(i).text(), embeddings.get(i)))
        .toList();
  }

  /**
   * One embedding per chunk, in chunk order.
   *
   * @throws EmbedderUnavailable if the embedder fails or breaks its contract of one embedding per
   *     passage
   */
  private List<Embedding> embeddingsFor(PostToIndex post, List<Chunk> chunks) {
    List<Embedding> embeddings =
        embedder.embedPassages(composition.compose(post.title(), chunks, tokens, formatter));
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
