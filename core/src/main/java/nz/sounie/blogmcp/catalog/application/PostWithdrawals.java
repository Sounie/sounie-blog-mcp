package nz.sounie.blogmcp.catalog.application;

import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.post.PostWithdrawn;
import nz.sounie.blogmcp.catalog.domain.post.WithdrawalReason;
import nz.sounie.blogmcp.shared.event.IntegrationEventPublisher;

/** Withdraws one post: raises the event, deletes the post, then publishes the event. */
final class PostWithdrawals {

  private final PostRepository posts;
  private final IntegrationEventPublisher events;

  PostWithdrawals(PostRepository posts, IntegrationEventPublisher events) {
    this.posts = posts;
    this.events = events;
  }

  void withdraw(Post post, WithdrawalReason reason) {
    PostWithdrawn withdrawn = post.withdraw(reason);
    posts.delete(post.id());
    events.publish(IntegrationEvents.from(withdrawn));
  }
}
