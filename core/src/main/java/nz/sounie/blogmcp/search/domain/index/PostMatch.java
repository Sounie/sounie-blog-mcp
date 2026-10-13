package nz.sounie.blogmcp.search.domain.index;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.embedding.Similarity;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;

/**
 * One post in the results: its metadata, its best entry's similarity, that entry's snippet, and the
 * matched image's URL when the best entry is an image.
 */
public record PostMatch(
    PostId postId,
    PostMetadata metadata,
    Similarity score,
    Snippet snippet,
    Optional<URI> matchedImage) {

  /** Score descending, then more recent published-at, then post ID ascending (as a string). */
  public static final Comparator<PostMatch> RANKING =
      Comparator.comparing(PostMatch::score)
          .reversed()
          .thenComparing(PostMatch::publishedAt, Comparator.reverseOrder())
          .thenComparing(match -> match.postId().external());

  public PostMatch {
    Objects.requireNonNull(matchedImage, "matchedImage");
  }

  private Instant publishedAt() {
    return metadata.publishedAt();
  }
}
