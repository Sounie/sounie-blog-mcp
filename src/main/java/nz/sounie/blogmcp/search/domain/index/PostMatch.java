package nz.sounie.blogmcp.search.domain.index;

import java.time.Instant;
import java.util.Comparator;
import nz.sounie.blogmcp.search.domain.embedding.Similarity;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;

/** One post in the results: its metadata, its best chunk's similarity, and that chunk's snippet. */
public record PostMatch(PostId postId, PostMetadata metadata, Similarity score, Snippet snippet) {

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
