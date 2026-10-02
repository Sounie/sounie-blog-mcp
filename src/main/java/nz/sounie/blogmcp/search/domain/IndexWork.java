package nz.sounie.blogmcp.search.domain;

import java.util.Objects;

/** What an index decision is applied with: the vector index and the post indexer. */
public record IndexWork(VectorIndex index, PostIndexer indexer) {

  public IndexWork {
    Objects.requireNonNull(index, "index");
    Objects.requireNonNull(indexer, "indexer");
  }

  /** Embeds the post, then saves the whole entry (embed before mutate). */
  void indexAndSave(PostToIndex post) {
    index.save(indexer.index(post));
  }

  /** The decision for the post against its current entry and the current recipe. */
  IndexDecision decisionFor(PostToIndex post) {
    return IndexDecision.forPost(index.find(post.id()), post, indexer.fingerprintOf(post));
  }
}
