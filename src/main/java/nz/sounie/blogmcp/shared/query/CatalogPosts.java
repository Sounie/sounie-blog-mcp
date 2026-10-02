package nz.sounie.blogmcp.shared.query;

import java.util.List;

/** Query contract: the catalog's current posts (ADR 0005). */
public interface CatalogPosts {

  /**
   * Every stored post, including summary-only ones, sorted by post ID (plain string order), each
   * post ID once.
   */
  List<CatalogPostState> currentPosts();
}
