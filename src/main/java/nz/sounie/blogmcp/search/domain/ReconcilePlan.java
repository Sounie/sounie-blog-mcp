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

  /** Catalog posts in catalog order, then orphans by post ID. */
  public static ReconcilePlan between(
      List<PostToIndex> catalog, VectorIndex snapshot, IndexRecipe recipe) {
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
}
