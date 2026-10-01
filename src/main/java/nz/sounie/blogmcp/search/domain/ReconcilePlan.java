package nz.sounie.blogmcp.search.domain;

import java.util.List;

/**
 * One index decision per catalog post (through {@link IndexDecision#forPost}) plus a remove per
 * orphan. Pure: never embeds.
 */
public record ReconcilePlan(List<IndexDecision> decisions) {

  public static ReconcilePlan between(
      List<PostToIndex> catalog, VectorIndex snapshot, IndexRecipe recipe) {
    throw new UnsupportedOperationException("not implemented");
  }
}
