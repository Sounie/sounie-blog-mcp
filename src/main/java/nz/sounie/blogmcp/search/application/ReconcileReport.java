package nz.sounie.blogmcp.search.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.PostId;

/** The outcome of a reconcile, per post, in the order the plan applied them. */
public record ReconcileReport(Map<PostId, IndexOutcome> outcomes) {

  public ReconcileReport {
    outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
  }

  public int count(IndexOutcome outcome) {
    return (int) outcomes.values().stream().filter(outcome::equals).count();
  }

  /** The post IDs whose outcome was {@code FAILED}. */
  public List<PostId> failed() {
    return outcomes.entrySet().stream()
        .filter(entry -> entry.getValue() == IndexOutcome.FAILED)
        .map(Map.Entry::getKey)
        .toList();
  }
}
