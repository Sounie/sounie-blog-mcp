package nz.sounie.blogmcp.search.application;

import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakePostCatalog;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import nz.sounie.blogmcp.search.domain.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.PassageComposition;
import nz.sounie.blogmcp.search.domain.PostIndexer;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/**
 * The search use cases wired as in production, over the real in-memory index and fakes of our own
 * ports (1 token per word, deterministic embedder).
 */
public final class SearchContext {

  public final InMemoryVectorIndex index = new InMemoryVectorIndex();
  public final FakeEmbedder embedder;
  public final PostIndexer indexer;
  public final IndexWriteLock lock = new IndexWriteLock();
  public final FakePostCatalog catalog = new FakePostCatalog();
  public final IndexPost indexPost;
  public final SearchPosts searchPosts;
  public final ReconcileIndex reconcile;

  public SearchContext() {
    this(new FakeEmbedder());
  }

  public SearchContext(FakeEmbedder embedder) {
    this.embedder = embedder;
    this.indexer = indexerWith(embedder);
    this.indexPost = new IndexPost(index, indexer, lock);
    this.searchPosts = new SearchPosts(index, embedder);
    this.reconcile = new ReconcileIndex(catalog, index, indexer, lock);
  }

  public static PostIndexer indexerWith(FakeEmbedder embedder) {
    return new PostIndexer(
        ChunkingPolicy.standard(),
        PassageComposition.standard(),
        FakeTokenCounter.perWord(1),
        embedder);
  }

  /** A reconcile over the same index and catalog with a different embedder (e.g. a new model). */
  public ReconcileIndex reconcileWith(FakeEmbedder other) {
    return new ReconcileIndex(catalog, index, indexerWith(other), lock);
  }

  public VectorIndex vectorIndex() {
    return index;
  }
}
