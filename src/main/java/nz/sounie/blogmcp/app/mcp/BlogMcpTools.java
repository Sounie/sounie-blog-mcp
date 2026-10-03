package nz.sounie.blogmcp.app.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.io.PrintStream;
import nz.sounie.blogmcp.catalog.application.GetPost;
import nz.sounie.blogmcp.search.application.SearchPosts;

/**
 * The two tool specifications. Each handler reads as a straight line: parse the arguments, call the
 * use case, present the result; expected failures become a {@link ToolOutcome}.
 */
public final class BlogMcpTools {

  public BlogMcpTools(
      SearchPosts searchPosts, GetPost getPost, SiteChoices sites, PrintStream errors) {
    // red: the implementer keeps the collaborators
  }

  public SyncToolSpecification searchPosts() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  public SyncToolSpecification getPost() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }
}
