package nz.sounie.blogmcp.catalog.domain.post;

import java.util.Objects;
import java.util.Optional;

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
    throw new UnsupportedOperationException("not implemented yet (app.md 3.7)");
  }

  /** The referenced post, if the repository holds it. */
  Optional<Post> lookUpIn(PostRepository posts);

  /** A post ID such as {@code sounie-wp:123}. */
  record ById(PostId id) implements PostReference {
    public ById {
      Objects.requireNonNull(id, "id");
    }

    @Override
    public Optional<Post> lookUpIn(PostRepository posts) {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.7)");
    }
  }

  /** An absolute http(s) URL, compared with canonical URLs after normalisation. */
  record ByUrl(WebAddress address) implements PostReference {
    public ByUrl {
      Objects.requireNonNull(address, "address");
    }

    @Override
    public Optional<Post> lookUpIn(PostRepository posts) {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.7)");
    }
  }
}
