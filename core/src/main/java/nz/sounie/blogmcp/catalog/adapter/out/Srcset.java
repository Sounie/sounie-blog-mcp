package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.Optional;

/**
 * The {@code srcset} attribute of an {@code <img>}. Its choice is the candidate with the largest
 * width descriptor ({@code w}) of at most 2048, the first on a tie. Candidates with {@code x}
 * descriptors, no descriptor, or an unparseable one are ignored.
 */
record Srcset(String attribute) {

  /** The chosen candidate URL, as written; empty when no candidate qualifies. */
  Optional<String> chosenUrl() {
    throw new UnsupportedOperationException("Srcset.chosenUrl is not implemented yet");
  }
}
