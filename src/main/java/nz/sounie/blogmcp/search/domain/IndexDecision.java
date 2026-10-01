package nz.sounie.blogmcp.search.domain;

import java.util.Optional;

/** What must happen to one post's index entry. */
public sealed interface IndexDecision {

  PostId postId();

  IndexOutcome apply(IndexWork work);

  /**
   * The single entry point for upserts, used by events and reconciles. Delegates to the post's
   * {@link Completeness}.
   */
  static IndexDecision forPost(
      Optional<IndexedPost> existing, PostToIndex post, ContentFingerprint current) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** A {@code FULL} post with no entry. */
  static IndexDecision forAbsent(PostToIndex post) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** A withdrawal or an orphan. */
  static IndexDecision remove(PostId id) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Not indexed yet: index and save. Outcome {@code ADDED}. */
  record Add(PostToIndex post) implements IndexDecision {
    @Override
    public PostId postId() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** The fingerprint differs: index again and replace. Outcome {@code RE_EMBEDDED}. */
  record ReEmbed(PostToIndex post) implements IndexDecision {
    @Override
    public PostId postId() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** Same fingerprint, different metadata: save without embedding. */
  record RefreshMetadata(IndexedPost existing, PostMetadata metadata) implements IndexDecision {
    @Override
    public PostId postId() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** Nothing differs. Outcome {@code UNCHANGED}. */
  record Keep(PostId postId) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** A summary-only post: remove any entry, never embed. */
  record Exclude(PostId postId) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** Withdrawn, or an orphan. */
  record Remove(PostId postId) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
