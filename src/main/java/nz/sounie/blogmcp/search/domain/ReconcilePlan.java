package nz.sounie.blogmcp.search.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One index decision per catalog entry with a known post ID (through {@link IndexDecision#forPost}
 * for readable posts) plus a remove per orphan, unless orphan removals are suppressed. Pure: never
 * embeds.
 */
public record ReconcilePlan(List<IndexDecision> decisions, boolean orphanRemovalSuppressed) {

  public ReconcilePlan {
    decisions = List.copyOf(decisions);
  }

  /**
   * Catalog posts in catalog order, then orphans by post ID. An unreadable entry plans {@link
   * IndexDecision.Unreadable} and is not an orphan; any unidentified entry suppresses every orphan
   * removal. A post ID listed twice is rejected as unreadable, not merged.
   */
  public static ReconcilePlan between(
      List<CatalogEntry> entries, VectorIndex snapshot, IndexRecipe recipe) {
    List<CatalogEntry> listed = listedOnce(entries);
    // Compile shim from the red step (review loop 2): the implementer derives suppression from
    // CatalogEntry.orphanRemovalBlockers() and keeps the reasons (orphanRemovalSuppressedBy).
    boolean suppressed = listed.stream().anyMatch(CatalogEntry.Unidentified.class::isInstance);
    Stream<IndexDecision> planned = listed.stream().flatMap(e -> e.decisions(snapshot, recipe));
    return new ReconcilePlan(
        Stream.concat(planned, orphanRemovals(listed, snapshot, suppressed)).toList(), suppressed);
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

  /** A remove per indexed post that no entry names, unless removals are suppressed. */
  private static Stream<IndexDecision> orphanRemovals(
      List<CatalogEntry> listed, VectorIndex snapshot, boolean suppressed) {
    Set<PostId> current =
        listed.stream()
            .map(CatalogEntry::knownId)
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    return snapshot.ids().stream()
        .filter(id -> !suppressed && !current.contains(id))
        .sorted(Comparator.comparing(PostId::external))
        .map(IndexDecision::remove);
  }

  /**
   * The reasons of the unidentified catalog entries that suppressed orphan removal in this run, in
   * catalog order; empty when orphan removal is allowed.
   */
  public List<String> orphanRemovalSuppressedBy() {
    throw new UnsupportedOperationException("not implemented");
  }
}
