package nz.sounie.blogmcp.search.domain.index;

import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;

/**
 * Identity of the method used to build an index entry, e.g. {@code
 * bge-small-en-v1.5-q/w300-t400-o50-oc100-c64/tt64-p1}: model ID, chunking parameters (target
 * words, body tokens, overlap words, overlap token cap, maximum word characters), then title tokens
 * and passage composition version. Changing any part makes every entry stale.
 */
public record IndexRecipe(String id) {

  /**
   * @throws IllegalArgumentException if the ID is blank
   */
  public IndexRecipe {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("An index recipe needs an ID");
    }
  }

  public static IndexRecipe of(
      String modelId, ChunkingPolicy chunking, PassageComposition composition) {
    return new IndexRecipe(
        String.join("/", modelId, chunking.recipePart(), composition.recipePart()));
  }
}
