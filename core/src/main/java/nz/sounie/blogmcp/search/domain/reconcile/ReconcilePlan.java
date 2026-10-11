package nz.sounie.blogmcp.search.domain.reconcile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.index.IndexDecision;
import nz.sounie.blogmcp.search.domain.index.IndexRecipe;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;

/**
 * One index decision per catalog entry with a known post ID (through {@link IndexDecision#forPost}
 * for readable posts) plus a remove per orphan, unless orphan removals are suppressed. Pure: never
 * embeds.
 */
public record ReconcilePlan(List<IndexDecision> decisions, List<String> orphanRemovalSuppressedBy) {

  /**
   * @param orphanRemovalSuppressedBy the reasons of the unidentified catalog entries that
   *     suppressed orphan removal in this run, in catalog order; empty when orphan removal is
   *     allowed
   */
  public ReconcilePlan {
    decisions = List.copyOf(decisions);
    orphanRemovalSuppressedBy = List.copyOf(orphanRemovalSuppressedBy);
  }

  /** Whether any unidentified catalog entry stopped orphans being removed in this run. */
  public boolean orphanRemovalSuppressed() {
    return !orphanRemovalSuppressedBy.isEmpty();
  }

  /**
   * Catalog posts in catalog order, then orphans by post ID. An unreadable entry plans {@link
   * IndexDecision.Unreadable} and is not an orphan; any unidentified entry suppresses every orphan
   * removal. A post ID listed twice is rejected as unreadable, not merged.
   */
  public static ReconcilePlan between(
      List<CatalogEntry> entries, VectorIndex snapshot, IndexRecipe recipe) {
    List<CatalogEntry> listed = listedOnce(entries);
    List<String> blockers = entries.stream().flatMap(CatalogEntry::orphanRemovalBlockers).toList();
    Stream<IndexDecision> planned = listed.stream().flatMap(e -> e.decisions(snapshot, recipe));
    Stream<IndexDecision> removals =
        blockers.isEmpty() ? orphanRemovals(listed, snapshot) : Stream.empty();
    return new ReconcilePlan(Stream.concat(planned, removals).toList(), blockers);
  }

  /** Every entry once: a post ID listed more than once becomes one unreadable duplicate. */
  private static List<CatalogEntry> listedOnce(List<CatalogEntry> entries) {
    Set<PostId> duplicated =
        entries.stream()
            .map(CatalogEntry::knownId)
            .flatMap(Optional::stream)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(count -> count.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    return entries.stream().map(entry -> entry.listedOnce(duplicated)).distinct().toList();
  }

  /** A remove per indexed post that no entry names. */
  private static Stream<IndexDecision> orphanRemovals(
      List<CatalogEntry> listed, VectorIndex snapshot) {
    Set<PostId> current =
        listed.stream()
            .map(CatalogEntry::knownId)
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    return snapshot.ids().stream()
        .filter(id -> !current.contains(id))
        .sorted(Comparator.comparing(PostId::external))
        .map(IndexDecision::remove);
  }
}
