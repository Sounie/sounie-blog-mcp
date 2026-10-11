package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.index.IndexDecision;
import nz.sounie.blogmcp.search.domain.index.IndexOutcome;
import nz.sounie.blogmcp.search.domain.post.PostId;

/** What applying one decision of a reconcile did to one post, with the reason if it failed. */
record PostOutcome(PostId postId, IndexOutcome outcome, Optional<String> failureReason) {

  PostOutcome {
    Objects.requireNonNull(postId, "postId");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(failureReason, "failureReason");
  }

  /** The decision was applied; an unreadable decision carries its own failure reason. */
  static PostOutcome applied(IndexDecision decision, IndexOutcome outcome) {
    return new PostOutcome(decision.postId(), outcome, decision.failureReason());
  }

  /** Applying the decision threw; the exception's message is the reason. */
  static PostOutcome failed(IndexDecision decision, RuntimeException failure) {
    return new PostOutcome(
        decision.postId(),
        IndexOutcome.FAILED,
        Optional.of(Objects.toString(failure.getMessage(), failure.getClass().getName())));
  }
}
