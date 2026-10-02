package nz.sounie.blogmcp.search.domain;

/** What an integration event asks of the index. */
public sealed interface IndexChange {

  /** Decides and applies the resulting index decision. */
  IndexOutcome applyTo(IndexWork work);

  /** Publish or revise. */
  record Upsert(PostToIndex post) implements IndexChange {
    @Override
    public IndexOutcome applyTo(IndexWork work) {
      return work.decisionFor(post).apply(work);
    }
  }

  /** Withdraw. */
  record Remove(PostId postId) implements IndexChange {
    @Override
    public IndexOutcome applyTo(IndexWork work) {
      return IndexDecision.remove(postId).apply(work);
    }
  }
}
