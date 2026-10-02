package nz.sounie.blogmcp.search.domain.index;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;

/** A chunk's index and text, with its embedding. */
record IndexedChunk(int index, String text, Embedding embedding) {

  public IndexedChunk {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(embedding, "embedding");
  }

  /** How similar this chunk is to the query. */
  public ChunkHit hitFor(Embedding query) {
    return new ChunkHit(index, text, embedding.similarityTo(query));
  }
}
