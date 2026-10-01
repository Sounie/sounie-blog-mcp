package nz.sounie.blogmcp.search.application;

import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.PostId;

/** The outcome of a reconcile, per post. */
public record ReconcileReport(Map<PostId, IndexOutcome> outcomes) {

  public int count(IndexOutcome outcome) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** The post IDs whose outcome was {@code FAILED}. */
  public List<PostId> failed() {
    throw new UnsupportedOperationException("not implemented");
  }
}
