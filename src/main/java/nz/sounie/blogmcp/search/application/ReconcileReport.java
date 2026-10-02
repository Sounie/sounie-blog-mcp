package nz.sounie.blogmcp.search.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.PostId;

/**
 * The outcome of a reconcile, per post, in the order the plan applied them; why each failed post
 * failed; and whether orphan removals were withheld because a catalog entry was unidentified.
 */
public record ReconcileReport(
    Map<PostId, IndexOutcome> outcomes,
    Map<PostId, String> failureReasons,
    boolean orphanRemovalSuppressed) {

  public ReconcileReport {
    outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    failureReasons = Collections.unmodifiableMap(new LinkedHashMap<>(failureReasons));
  }

  static ReconcileReport of(List<PostOutcome> results, boolean orphanRemovalSuppressed) {
    Map<PostId, IndexOutcome> outcomes = new LinkedHashMap<>();
    Map<PostId, String> reasons = new LinkedHashMap<>();
    results.forEach(result -> outcomes.put(result.postId(), result.outcome()));
    results.forEach(
        result -> result.failureReason().ifPresent(reason -> reasons.put(result.postId(), reason)));
    return new ReconcileReport(outcomes, reasons, orphanRemovalSuppressed);
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

  /**
   * Warning {@code ORPHAN_REMOVAL_SUPPRESSED}: the reasons of the unidentified catalog entries that
   * stopped orphan removal in this run; empty when orphans were removed as usual.
   */
  public List<String> orphanRemovalSuppressedReasons() {
    throw new UnsupportedOperationException("not implemented");
  }
}
