package nz.sounie.blogmcp.search.domain;

/**
 * Identity of the method used to build an index entry, e.g. {@code
 * bge-small-en-v1.5-q/w300-o50-t400-tt64-c64/p1}. Changing any part makes every entry stale.
 */
public record IndexRecipe(String id) {

  public static IndexRecipe of(
      String modelId, ChunkingPolicy chunking, PassageComposition composition) {
    throw new UnsupportedOperationException("not implemented");
  }
}
