package nz.sounie.blogmcp.search.domain.index;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;

/** A chunk's index and text, with its embedding. */
public record IndexedChunk(int index, String text, Embedding embedding) implements IndexEntry {

  public IndexedChunk {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(embedding, "embedding");
  }

  @Override
  public Kind kind() {
    return Kind.CHUNK;
  }

  /** The start of the chunk's text (without the title line). */
  @Override
  public Snippet snippet() {
    return Snippet.of(text);
  }

  @Override
  public Optional<URI> matchedImage() {
    return Optional.empty();
  }
}
