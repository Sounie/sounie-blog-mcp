package nz.sounie.blogmcp.search.application;

import java.util.List;
import nz.sounie.blogmcp.search.domain.CatalogEntry;

/** Port: the catalog's current posts, each as readable, unreadable or unidentified. */
public interface PostCatalog {

  List<CatalogEntry> currentPosts();
}
