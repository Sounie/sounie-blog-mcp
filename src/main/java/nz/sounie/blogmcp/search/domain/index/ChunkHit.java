package nz.sounie.blogmcp.search.domain.index;

import java.util.Comparator;
import nz.sounie.blogmcp.search.domain.embedding.Similarity;

/** The similarity of one indexed chunk to the query. */
record ChunkHit(int chunkIndex, String text, Similarity similarity) {

  /** Similarity descending, then chunk index ascending. */
  public static final Comparator<ChunkHit> BEST_FIRST =
      Comparator.comparing(ChunkHit::similarity).reversed().thenComparingInt(ChunkHit::chunkIndex);
}
