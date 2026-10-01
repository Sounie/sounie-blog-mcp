package nz.sounie.blogmcp.search.domain;

import java.util.List;
import java.util.function.Predicate;

/** The site filter and the date range as one predicate over post metadata; all rules must hold. */
public record SearchFilters(List<Predicate<PostMetadata>> rules) {

  public SearchFilters {
    rules = List.copyOf(rules);
  }

  public static SearchFilters of(SiteFilter site, PublishedDateRange dates) {
    return new SearchFilters(
        List.of(
            metadata -> site.includes(metadata.siteId()),
            metadata -> dates.includes(metadata.publishedAt())));
  }

  public boolean includes(PostMetadata metadata) {
    return rules.stream().allMatch(rule -> rule.test(metadata));
  }
}
