package nz.sounie.blogmcp.search.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One index decision per catalog post (through {@link IndexDecision#forPost}) plus a remove per
 * orphan. Pure: never embeds.
 */
public record ReconcilePlan(List<IndexDecision> decisions) {

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
    // Compile shim from the red step (fix loop 1): reads only Readable entries, as before.
    // The implementer replaces this with the AC-SRCH-38 rules above.
    List<PostToIndex> catalog =
        entries.stream()
            .filter(CatalogEntry.Readable.class::isInstance)
            .map(entry -> ((CatalogEntry.Readable) entry).post())
            .toList();
    Set<PostId> current = catalog.stream().map(PostToIndex::id).collect(Collectors.toSet());
    Stream<IndexDecision> upserts =
        catalog.stream()
            .map(
                post ->
                    IndexDecision.forPost(
                        snapshot.find(post.id()), post, post.fingerprintUnder(recipe)));
    Stream<IndexDecision> orphans =
        snapshot.ids().stream()
            .filter(id -> !current.contains(id))
            .sorted(Comparator.comparing(PostId::external))
            .map(IndexDecision::remove);
    return new ReconcilePlan(Stream.concat(upserts, orphans).toList());
  }

  /**
   * Whether orphan removals were withheld because some catalog entry could not be identified
   * (AC-SRCH-38).
   */
  public boolean withdrawalsSuppressed() {
    throw new UnsupportedOperationException("not implemented");
  }
}
