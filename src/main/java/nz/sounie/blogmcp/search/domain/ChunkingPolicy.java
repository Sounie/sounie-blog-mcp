package nz.sounie.blogmcp.search.domain;

import java.util.List;

/** Decides chunk boundaries and overlap (search.md 3.4). */
public record ChunkingPolicy(
    int targetWords, int bodyTokenBudget, int overlapWords, int overlapTokenCap) {

  public static final int TARGET_WORDS = 300;
  public static final int BODY_TOKEN_BUDGET = 400;
  public static final int OVERLAP_WORDS = 50;
  public static final int OVERLAP_TOKEN_CAP = 100;

  /** 300 words, 400 body tokens, overlap of up to 50 words and 100 tokens. */
  public static ChunkingPolicy standard() {
    return new ChunkingPolicy(TARGET_WORDS, BODY_TOKEN_BUDGET, OVERLAP_WORDS, OVERLAP_TOKEN_CAP);
  }

  /** An empty word sequence gives one chunk with empty text. */
  public List<Chunk> chunk(WordSequence words, TokenCounter tokens) {
    throw new UnsupportedOperationException("not implemented");
  }
}
