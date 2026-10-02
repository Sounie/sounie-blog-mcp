package nz.sounie.blogmcp.search.domain;

import java.time.Instant;
import java.util.Comparator;

/** One post in the results: its metadata, its best chunk's similarity, and that chunk's text. */
public record PostMatch(PostId postId, PostMetadata metadata, Similarity score, String snippet) {

  /** Score descending, then more recent published-at, then post ID ascending (as a string). */
  public static final Comparator<PostMatch> RANKING =
      Comparator.comparing(PostMatch::score)
          .reversed()
          .thenComparing(PostMatch::publishedAt, Comparator.reverseOrder())
          .thenComparing(match -> match.postId().external());

  private Instant publishedAt() {
    return metadata.publishedAt();
  }
}
