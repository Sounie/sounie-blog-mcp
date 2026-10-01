package nz.sounie.blogmcp.search.domain;

import java.util.Comparator;

/** The similarity of one indexed chunk to the query. */
public record ChunkHit(int chunkIndex, String text, Similarity similarity) {

  /** Similarity descending, then chunk index ascending. */
  public static final Comparator<ChunkHit> BEST_FIRST =
      Comparator.comparing(ChunkHit::similarity).reversed().thenComparingInt(ChunkHit::chunkIndex);
}
