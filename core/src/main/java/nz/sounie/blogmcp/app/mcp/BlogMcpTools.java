package nz.sounie.blogmcp.app.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.application.GetPost;
import nz.sounie.blogmcp.search.application.FindPostsLikeImage;
import nz.sounie.blogmcp.search.application.ImageSearch;
import nz.sounie.blogmcp.search.application.SearchPosts;
import nz.sounie.blogmcp.search.domain.query.SearchResults;

/**
 * The tool specifications: {@code search_posts} and {@code get_post}, and {@code
 * find_posts_like_image} once given an image search (app.md 3.9). Each handler reads as a straight
 * line: parse the arguments, call the use case, present the result; expected failures become a
 * {@link ToolOutcome}.
 */
public final class BlogMcpTools {

  private static final String POST = "post";
  private static final Set<String> GET_POST_ARGUMENTS = Set.of(POST);

  private final SearchPosts searchPosts;
  private final GetPost getPost;
  private final Optional<FindPostsLikeImage> findPostsLikeImage;
  private final SiteChoices sites;
  private final PrintStream errors;
  private final ToolFailures failures;
  private final McpJsonMapper mapper = McpJsonDefaults.getMapper();

  /** The text-only tools: {@code search_posts} and {@code get_post}. */
  public BlogMcpTools(
      SearchPosts searchPosts, GetPost getPost, SiteChoices sites, PrintStream errors) {
    this(searchPosts, getPost, Optional.empty(), sites, errors);
  }

  private BlogMcpTools(
      SearchPosts searchPosts,
      GetPost getPost,
      Optional<FindPostsLikeImage> findPostsLikeImage,
      SiteChoices sites,
      PrintStream errors) {
    this.searchPosts = Objects.requireNonNull(searchPosts, "searchPosts");
    this.getPost = Objects.requireNonNull(getPost, "getPost");
    this.findPostsLikeImage = findPostsLikeImage;
    this.sites = Objects.requireNonNull(sites, "sites");
    this.errors = Objects.requireNonNull(errors, "errors");
    this.failures = new ToolFailures(errors);
  }

  /** These tools, plus {@code find_posts_like_image} over the given use case. */
  public BlogMcpTools withImageSearch(FindPostsLikeImage imageSearch) {
    return new BlogMcpTools(
        searchPosts,
        getPost,
        Optional.of(Objects.requireNonNull(imageSearch, "imageSearch")),
        sites,
        errors);
  }

  /** Every tool to register, in order: search, get, and find by image when there is one. */
  public List<SyncToolSpecification> specifications() {
    return Stream.concat(
            Stream.of(searchPosts(), getPost()),
            findPostsLikeImage.stream().map(this::findPostsLikeImage))
        .toList();
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

  private SyncToolSpecification findPostsLikeImage(FindPostsLikeImage useCase) {
    return SyncToolSpecification.builder()
        .tool(ToolDefinitions.findPostsLikeImage(mapper, sites))
        .callHandler(
            (exchange, request) ->
                failures.guard(() -> findLikeImage(useCase, request)).toResult(mapper))
        .build();
  }

  private ToolOutcome search(CallToolRequest request) {
    return answered(
        searchPosts.search(SearchPostsArguments.read(request.arguments()).toQuery(sites)));
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

  private ToolOutcome findLikeImage(FindPostsLikeImage useCase, CallToolRequest request) {
    FindPostsLikeImageArguments arguments = FindPostsLikeImageArguments.read(request.arguments());
    return outcome(useCase.find(arguments.toQuery(sites)), arguments.image());
  }

  /** A refused or unavailable image is a tool error, because the request cannot be answered. */
  private static ToolOutcome outcome(ImageSearch search, String image) {
    return switch (search) {
      case ImageSearch.Ranked ranked -> answered(ranked.results());
      case ImageSearch.ImageRefused refused ->
          new ToolOutcome.Failed(
              "Cannot use image '" + image + "': " + refused.reason().explanation());
      case ImageSearch.ImageUnavailable unavailable ->
          new ToolOutcome.Failed("Could not load image '" + image + "': " + unavailable.detail());
    };
  }

  private static ToolOutcome answered(SearchResults results) {
    return new ToolOutcome.Answered(
        Map.of("results", results.matches().stream().map(SearchResultView::of).toList()));
  }
}
