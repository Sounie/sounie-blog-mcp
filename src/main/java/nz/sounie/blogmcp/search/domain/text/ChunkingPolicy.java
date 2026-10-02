package nz.sounie.blogmcp.search.domain.text;

import java.util.ArrayList;
import java.util.List;

/** Decides chunk boundaries and overlap (search.md 3.4). */
public record ChunkingPolicy(
    int targetWords, int bodyTokenBudget, int overlapWords, int overlapTokenCap) {

  public static final int TARGET_WORDS = 300;
  public static final int BODY_TOKEN_BUDGET = 400;
  public static final int OVERLAP_WORDS = 50;
  public static final int OVERLAP_TOKEN_CAP = 100;

  /**
   * @throws IllegalArgumentException if the target or budget is not positive, or an overlap limit
   *     is negative
   */
  public ChunkingPolicy {
    requireAtLeast(1, targetWords, "targetWords");
    requireAtLeast(1, bodyTokenBudget, "bodyTokenBudget");
    requireAtLeast(0, overlapWords, "overlapWords");
    requireAtLeast(0, overlapTokenCap, "overlapTokenCap");
  }

  private static void requireAtLeast(int minimum, int value, String name) {
    if (value < minimum) {
      throw new IllegalArgumentException(name + " must be at least " + minimum + ": " + value);
    }
  }

  /** 300 words, 400 body tokens, overlap of up to 50 words and 100 tokens. */
  public static ChunkingPolicy standard() {
    return new ChunkingPolicy(TARGET_WORDS, BODY_TOKEN_BUDGET, OVERLAP_WORDS, OVERLAP_TOKEN_CAP);
  }

  /** The chunking part of the index recipe, e.g. {@code w300-t400-o50-oc100-c64}. */
  public String recipePart() {
    return "w%d-t%d-o%d-oc%d-c%d"
        .formatted(
            targetWords,
            bodyTokenBudget,
            overlapWords,
            overlapTokenCap,
            WordSequence.MAX_WORD_CHARS);
  }

  /** An empty word sequence gives one chunk with empty text. */
  public List<Chunk> chunk(WordSequence words, TokenCounter tokens) {
    return words.isEmpty()
        ? List.of(new Chunk(0, List.of()))
        : windows(words.words(), WordCosts.of(words.words(), tokens));
  }

  /**
   * Grows each chunk until the next word would break the word target or the token budget (always
   * taking at least one word), then starts the next chunk at the overlap. Stops after the chunk
   * holding the last word.
   */
  private List<Chunk> windows(List<String> words, WordCosts costs) {
    List<Chunk> chunks = new ArrayList<>();
    int start = 0;
    int end = 0;
    while (end < costs.size()) {
      end = start + Math.max(1, costs.fittingFrom(start, targetWords, bodyTokenBudget));
      chunks.add(new Chunk(chunks.size(), words.subList(start, end)));
      start = overlapStart(costs, start, end);
    }
    return chunks;
  }

  /**
   * The start of the longest suffix of {@code start..end} within the overlap limits, but always at
   * least one word after {@code start}. The overlap also leaves room in the body budget for the
   * next word, so every chunk adds a new word even after an unusually expensive one.
   */
  private int overlapStart(WordCosts costs, int start, int end) {
    int room = Math.min(overlapTokenCap, bodyTokenBudget - costs.costAt(end));
    int overlap = costs.fittingBefore(end, overlapWords, room);
    return end - Math.min(overlap, end - start - 1);
  }
}
