package nz.sounie.blogmcp.catalog.domain.sync;

/**
 * The order in which a blog source returns changed entries. Decides when the checkpoint may move:
 * an oldest-first source has seen every earlier change once a page is handled, so its checkpoint
 * advances after every page; any other order may only advance after the last page.
 */
public enum ChangeOrder {
  OLDEST_FIRST {
    @Override
    public void afterPageHandled(Runnable advanceCheckpoint) {
      advanceCheckpoint.run();
    }
  },
  NEWEST_FIRST,
  UNORDERED;

  /** Called after each fully handled page; advances the checkpoint only if this order allows it. */
  public void afterPageHandled(Runnable advanceCheckpoint) {
    // Not safe to advance mid-run: an earlier change may still be on a later page.
  }

  /** Called after the last page of a complete run; every order may advance the checkpoint then. */
  public void afterLastPage(Runnable advanceCheckpoint) {
    advanceCheckpoint.run();
  }
}
