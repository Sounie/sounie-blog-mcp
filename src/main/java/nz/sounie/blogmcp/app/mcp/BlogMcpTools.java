package nz.sounie.blogmcp.app.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nz.sounie.blogmcp.catalog.application.GetPost;
import nz.sounie.blogmcp.search.application.SearchPosts;

/**
 * The two tool specifications. Each handler reads as a straight line: parse the arguments, call the
 * use case, present the result; expected failures become a {@link ToolOutcome}.
 */
public final class BlogMcpTools {

  private static final String POST = "post";
  private static final Set<String> GET_POST_ARGUMENTS = Set.of(POST);

  private final SearchPosts searchPosts;
  private final GetPost getPost;
  private final SiteChoices sites;
  private final ToolFailures failures;
  private final McpJsonMapper mapper = McpJsonDefaults.getMapper();

  public BlogMcpTools(
      SearchPosts searchPosts, GetPost getPost, SiteChoices sites, PrintStream errors) {
    this.searchPosts = Objects.requireNonNull(searchPosts, "searchPosts");
    this.getPost = Objects.requireNonNull(getPost, "getPost");
    this.sites = Objects.requireNonNull(sites, "sites");
    this.failures = new ToolFailures(errors);
  }

  public SyncToolSpecification searchPosts() {
    return SyncToolSpecification.builder()
        .tool(ToolDefinitions.searchPosts(mapper, sites))
        .callHandler((exchange, request) -> failures.guard(() -> search(request)).toResult(mapper))
        .build();
  }

  public SyncToolSpecification getPost() {
    return SyncToolSpecification.builder()
        .tool(ToolDefinitions.getPost(mapper))
        .callHandler((exchange, request) -> failures.guard(() -> find(request)).toResult(mapper))
        .build();
  }

  private ToolOutcome search(CallToolRequest request) {
    List<SearchResultView> results =
        searchPosts
            .search(SearchPostsArguments.read(request.arguments()).toQuery(sites))
            .matches()
            .stream()
            .map(SearchResultView::of)
            .toList();
    return new ToolOutcome.Answered(Map.of("results", results));
  }

  private ToolOutcome find(CallToolRequest request) {
    String reference =
        ToolArguments.of(request.arguments(), GET_POST_ARGUMENTS).requiredString(POST);
    return getPost
        .byReference(reference)
        .<ToolOutcome>map(
            post -> new ToolOutcome.Answered(Map.of("found", true, POST, GetPostView.of(post))))
        .orElseGet(() -> new ToolOutcome.NotFound("No post found for '" + reference + "'"));
  }
}
