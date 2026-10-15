package nz.sounie.blogmcp.search.adapter.out;

import java.net.InetAddress;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The image address categories a blog permits: those that every address of its post host permits
 * (search.md 3.7). A host with any public address counts as public; a host with both
 * private-network and non-routable addresses permits only what both permit.
 */
record BlogCategory(Set<AddressCategory> permitted) {

  /**
   * The query address rule (search.md 3.8, owner decision Q-IMG 12): a query image may come from
   * every address category while the server is stdio-only. Revisit when stdio moves to HTTP.
   */
  static final BlogCategory PERMIT_ALL = new BlogCategory(EnumSet.allOf(AddressCategory.class));

  BlogCategory {
    permitted = Set.copyOf(permitted);
  }

  /** No address permits nothing. */
  static BlogCategory of(List<InetAddress> postHostAddresses) {
    return new BlogCategory(
        postHostAddresses.stream()
            .map(address -> AddressCategory.of(address).permitted())
            .reduce(BlogCategory::intersection)
            .orElseGet(() -> EnumSet.noneOf(AddressCategory.class)));
  }

  private static Set<AddressCategory> intersection(
      Set<AddressCategory> first, Set<AddressCategory> second) {
    Set<AddressCategory> both = EnumSet.copyOf(first);
    both.retainAll(second);
    return both;
  }

  /** Whether the image host may be fetched: it has addresses, and each one is permitted. */
  boolean permitsAll(List<InetAddress> imageHostAddresses) {
    return !imageHostAddresses.isEmpty()
        && imageHostAddresses.stream().map(AddressCategory::of).allMatch(permitted::contains);
  }
}
