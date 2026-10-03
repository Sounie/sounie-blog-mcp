package nz.sounie.blogmcp.catalog.domain.post;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The text given to look up one post: a URL (it starts with a scheme and {@code ://}) or a post ID
 * (anything else). Syntax is decided here; whether the post exists is the repository's answer.
 */
public sealed interface PostReference {

  /**
   * Parses trimmed text: {@link ByUrl} when it matches {@code ^[A-Za-z][A-Za-z0-9+.-]*://},
   * otherwise {@link ById}.
   *
   * @throws InvalidPostReference if the text is blank, or does not parse as its form
   */
  static PostReference parse(String text) {
    String trimmed = Objects.requireNonNull(text, "text").strip();
    if (trimmed.isEmpty()) {
      throw new InvalidPostReference("A post reference must not be blank");
    }
    return Forms.URL_START.matcher(trimmed).lookingAt()
        ? new ByUrl(WebAddress.parse(trimmed))
        : ById.parse(trimmed);
  }

  /** The referenced post, if the repository holds it. */
  Optional<Post> lookUpIn(PostRepository posts);

  /** A post ID such as {@code sounie-wp:123}. */
  record ById(PostId id) implements PostReference {
    public ById {
      Objects.requireNonNull(id, "id");
    }

    /**
     * @throws InvalidPostReference if the text is not {@code <siteId>:<sourcePostId>}
     */
    public static ById parse(String text) {
      try {
        return new ById(PostId.parse(text));
      } catch (IllegalArgumentException e) {
        throw new InvalidPostReference("Not a post ID (<siteId>:<sourcePostId>): '" + text + "'");
      }
    }

    @Override
    public Optional<Post> lookUpIn(PostRepository posts) {
      return posts.findById(id);
    }
  }

  /** An absolute http(s) URL, compared with canonical URLs after normalisation. */
  record ByUrl(WebAddress address) implements PostReference {
    public ByUrl {
      Objects.requireNonNull(address, "address");
    }

    @Override
    public Optional<Post> lookUpIn(PostRepository posts) {
      return posts.findByCanonicalUrl(address.asHttps());
    }
  }
}

/** The textual forms a post reference can take. */
final class Forms {

  /** A scheme followed by {@code ://}: the reference is meant as a URL. */
  static final Pattern URL_START = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://");

  private Forms() {}
}
