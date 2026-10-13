package nz.sounie.blogmcp.search.domain.index;

import java.net.URI;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;

/** Anything a query is scored against: an indexed chunk or an indexed image. */
public sealed interface IndexEntry permits IndexedChunk, IndexedImage {

  /** Which kind of entry; on equal similarity, chunks come before images. */
  enum Kind {
    CHUNK,
    IMAGE
  }

  Kind kind();

  /** The chunk index, or the image index. */
  int index();

  Embedding embedding();

  /** What a match through this entry shows. */
  Snippet snippet();

  /** The image URL when this entry is an image; empty for a chunk. */
  Optional<URI> matchedImage();

  /** How similar this entry is to the query. */
  default EntryHit hitFor(Embedding query) {
    return new EntryHit(this, embedding().similarityTo(query));
  }
}
