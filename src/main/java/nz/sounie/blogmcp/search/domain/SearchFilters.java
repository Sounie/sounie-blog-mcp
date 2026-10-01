package nz.sounie.blogmcp.search.domain;

import java.util.List;
import java.util.function.Predicate;

/** The site filter and the date range as one predicate over post metadata; all rules must hold. */
public record SearchFilters(List<Predicate<PostMetadata>> rules) {

  public static SearchFilters of(SiteFilter site, PublishedDateRange dates) {
    throw new UnsupportedOperationException("not implemented");
  }

  public boolean includes(PostMetadata metadata) {
    throw new UnsupportedOperationException("not implemented");
  }
}
