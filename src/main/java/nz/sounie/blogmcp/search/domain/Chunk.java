package nz.sounie.blogmcp.search.domain;

import java.util.List;

/** A contiguous window of the word sequence, embedded as one unit. */
public record Chunk(int index, List<String> words) {

  /** The words joined by single spaces. */
  public String text() {
    throw new UnsupportedOperationException("not implemented");
  }
}
