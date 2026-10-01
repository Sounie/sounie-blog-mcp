package nz.sounie.blogmcp.search.domain;

import java.util.Comparator;

/** One post in the results: its metadata, its best chunk's similarity, and that chunk's text. */
public record PostMatch(PostId postId, PostMetadata metadata, Similarity score, String snippet) {

  /** Score descending, then more recent published-at, then post ID ascending (as a string). */
  public static final Comparator<PostMatch> RANKING =
      (a, b) -> {
        throw new UnsupportedOperationException("not implemented");
      };
}
