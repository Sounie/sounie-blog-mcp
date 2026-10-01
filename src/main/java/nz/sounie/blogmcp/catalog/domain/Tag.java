package nz.sounie.blogmcp.catalog.domain;

import java.util.Collection;
import java.util.Set;

/**
 * Plain-text label, trimmed and not blank. Two tags are equal when their values are equal ignoring
 * case.
 */
public record Tag(String value) {

  /**
   * Builds a tag set from raw labels: trims, drops blank labels, and removes case-insensitive
   * duplicates, keeping the casing seen first. Iteration order is the order first seen.
   */
  public static Set<Tag> setOf(Collection<String> rawLabels) {
    throw new UnsupportedOperationException("not implemented");
  }
}
