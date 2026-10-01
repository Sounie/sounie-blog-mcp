package nz.sounie.blogmcp.search.application;

import java.util.List;
import nz.sounie.blogmcp.search.domain.PostToIndex;

/** Port: the catalog's current posts, as posts to index. */
public interface PostCatalog {

  List<PostToIndex> currentPosts();
}
