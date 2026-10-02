package nz.sounie.blogmcp.catalog.domain.post;

/** A post was removed from the catalog. */
public record PostWithdrawn(PostId postId, CanonicalUrl url, WithdrawalReason reason)
    implements PostEvent {}
