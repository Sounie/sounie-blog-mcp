package nz.sounie.blogmcp.app.mcp;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.image.ImageQuery;
import nz.sounie.blogmcp.search.domain.query.ImageSearchQuery;

/**
 * The arguments of a {@code find_posts_like_image} call, and their mapping to an {@link
 * ImageSearchQuery} (app.md 3.9).
 */
public final class FindPostsLikeImageArguments {

  static final String IMAGE = "image";

  private static final Set<String> NAMES =
      Stream.concat(Stream.of(IMAGE), ScopeArguments.NAMES.stream())
          .collect(Collectors.toUnmodifiableSet());

  private final String image;
  private final ScopeArguments scope;

  private FindPostsLikeImageArguments(ToolArguments arguments) {
    this.image = arguments.requiredString(IMAGE).strip();
    this.scope = ScopeArguments.read(arguments);
  }

  /**
   * Reads {@code image}, {@code site}, {@code from}, {@code to} and {@code limit}.
   *
   * @throws InvalidToolArgument for a missing, mistyped or unknown argument
   */
  public static FindPostsLikeImageArguments read(Map<String, Object> raw) {
    return new FindPostsLikeImageArguments(ToolArguments.of(raw, NAMES));
  }

  /** The trimmed {@code image} text, as the person gave it. */
  String image() {
    return image;
  }

  /**
   * Parsing a local path also checks its file (search.md 3.8, owner decision Q-IMG 13).
   *
   * @throws nz.sounie.blogmcp.search.domain.image.InvalidImageQuery for an unusable image
   * @throws InvalidToolArgument for an unknown site
   * @throws nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery for {@code from} after {@code
   *     to}
   */
  public ImageSearchQuery toQuery(SiteChoices sites) {
    return new ImageSearchQuery(ImageQuery.parse(image), scope.toScope(sites));
  }
}
