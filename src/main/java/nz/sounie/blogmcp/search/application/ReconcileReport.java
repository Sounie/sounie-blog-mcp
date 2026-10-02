package nz.sounie.blogmcp.search.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.index.IndexOutcome;
import nz.sounie.blogmcp.search.domain.post.PostId;

/**
 * The outcome of a reconcile, per post, in the order the plan applied them; why each failed post
 * failed; and why orphan removal was suppressed, if an unidentified catalog entry stopped it.
 */
public record ReconcileReport(
    Map<PostId, IndexOutcome> outcomes,
    Map<PostId, String> failureReasons,
    List<String> orphanRemovalSuppressedReasons) {

  /**
   * @param orphanRemovalSuppressedReasons warning {@code ORPHAN_REMOVAL_SUPPRESSED}: the reasons of
   *     the unidentified catalog entries that stopped orphan removal in this run; empty when
   *     orphans were removed as usual
   */
  public ReconcileReport {
    outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    failureReasons = Collections.unmodifiableMap(new LinkedHashMap<>(failureReasons));
    orphanRemovalSuppressedReasons = List.copyOf(orphanRemovalSuppressedReasons);
  }

  static ReconcileReport of(List<PostOutcome> results, List<String> orphanRemovalSuppressedBy) {
    Map<PostId, IndexOutcome> outcomes = new LinkedHashMap<>();
    Map<PostId, String> reasons = new LinkedHashMap<>();
    results.forEach(result -> outcomes.put(result.postId(), result.outcome()));
    results.forEach(
        result -> result.failureReason().ifPresent(reason -> reasons.put(result.postId(), reason)));
    return new ReconcileReport(outcomes, reasons, orphanRemovalSuppressedBy);
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

  /** Whether any unidentified catalog entry stopped orphans being removed in this run. */
  public boolean orphanRemovalSuppressed() {
    return !orphanRemovalSuppressedReasons.isEmpty();
  }
}
