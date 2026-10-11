package nz.sounie.blogmcp.catalog.domain.post;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Plain-text label, trimmed and not blank. Two tags are equal when their values are equal ignoring
 * case.
 */
public record Tag(String value) {

  public Tag {
    Objects.requireNonNull(value, "tag");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tag must not be blank");
    }
  }

  /**
   * Builds a tag set from raw labels: trims, drops blank labels, and removes case-insensitive
   * duplicates, keeping the casing seen first. Iteration order is the order first seen.
   */
  public static Set<Tag> setOf(Collection<String> rawLabels) {
    Set<Tag> tags = new LinkedHashSet<>();
    for (String label : rawLabels) {
      if (label != null && !label.isBlank()) {
        tags.add(new Tag(label));
      }
    }
    return Collections.unmodifiableSet(tags);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Tag tag && comparable().equals(tag.comparable());
  }

  @Override
  public int hashCode() {
    return comparable().hashCode();
  }

  private String comparable() {
    return value.toLowerCase(Locale.ROOT);
  }
}
