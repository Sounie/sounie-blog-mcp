package nz.sounie.blogmcp.search.domain;

import java.util.Objects;
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
    return post.completeness().decide(existing, post, current);
  }

  /** A {@code FULL} post with no entry. */
  static IndexDecision forAbsent(PostToIndex post) {
    return new Add(post);
  }

  /** A withdrawal or an orphan. */
  static IndexDecision remove(PostId id) {
    return new Remove(id);
  }

  /** Not indexed yet: index and save. Outcome {@code ADDED}. */
  record Add(PostToIndex post) implements IndexDecision {
    public Add {
      Objects.requireNonNull(post, "post");
    }

    @Override
    public PostId postId() {
      return post.id();
    }

    @Override
    public IndexOutcome apply(IndexWork work) {
      work.indexAndSave(post);
      return IndexOutcome.ADDED;
    }
  }

  /** The fingerprint differs: index again and replace. Outcome {@code RE_EMBEDDED}. */
  record ReEmbed(PostToIndex post) implements IndexDecision {
    public ReEmbed {
      Objects.requireNonNull(post, "post");
    }

    @Override
    public PostId postId() {
      return post.id();
    }

    @Override
    public IndexOutcome apply(IndexWork work) {
      work.indexAndSave(post);
      return IndexOutcome.RE_EMBEDDED;
    }
  }

  /** Same fingerprint, different metadata: save without embedding. */
  record RefreshMetadata(IndexedPost existing, PostMetadata metadata) implements IndexDecision {
    public RefreshMetadata {
      Objects.requireNonNull(existing, "existing");
      Objects.requireNonNull(metadata, "metadata");
    }

    @Override
    public PostId postId() {
      return existing.id();
    }

    @Override
    public IndexOutcome apply(IndexWork work) {
      work.index().save(existing.withMetadata(metadata));
      return IndexOutcome.METADATA_REFRESHED;
    }
  }

  /** Nothing differs. Outcome {@code UNCHANGED}. */
  record Keep(PostId postId) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      return IndexOutcome.UNCHANGED;
    }
  }

  /** A summary-only post: remove any entry, never embed. */
  record Exclude(PostId postId) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      return work.index().remove(postId) ? IndexOutcome.REMOVED : IndexOutcome.EXCLUDED;
    }
  }

  /**
   * A catalog post search cannot read (AC-SRCH-38): leave any entry alone and report {@code FAILED}
   * with the reason.
   */
  record Unreadable(PostId postId, String reason) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** Withdrawn, or an orphan. */
  record Remove(PostId postId) implements IndexDecision {
    @Override
    public IndexOutcome apply(IndexWork work) {
      return work.index().remove(postId) ? IndexOutcome.REMOVED : IndexOutcome.ALREADY_ABSENT;
    }
  }
}
