package nz.sounie.blogmcp.catalog.domain;

/** Outcome of {@link Post#revise(Site, PostSnapshot)}. */
public sealed interface Revision {

  /** The snapshot is not older and something material changed. */
  record Changed(PostRevised event) implements Revision {}

  /** Only {@code updatedAt} moved forward; it was recorded and no event is raised. */
  record Touched() implements Revision {}

  /** Nothing changed. */
  record Unchanged() implements Revision {}

  /** The snapshot is older than the stored post and was ignored. */
  record Stale() implements Revision {}
}
