package nz.sounie.blogmcp.search.domain;

import java.util.List;

/** The token cost of each word of a sequence, and how many consecutive words fit a budget. */
final class WordCosts {

  private final int[] costs;

  private WordCosts(int[] costs) {
    this.costs = costs;
  }

  static WordCosts of(List<String> words, TokenCounter tokens) {
    return new WordCosts(words.stream().mapToInt(tokens::tokensIn).toArray());
  }

  int size() {
    return costs.length;
  }

  /** The cost of the word at {@code index}; past the last word there is nothing to pay for. */
  int costAt(int index) {
    return index < costs.length ? costs[index] : 0;
  }

  /**
   * How many words starting at {@code from} fit within both {@code maxWords} and {@code budget}.
   */
  int fittingFrom(int from, int maxWords, int budget) {
    int limit = Math.min(maxWords, costs.length - from);
    int count = 0;
    int left = budget;
    while (count < limit && costs[from + count] <= left) {
      left -= costs[from + count];
      count++;
    }
    return count;
  }

  /**
   * How many words ending just before {@code end} fit within both {@code maxWords} and {@code
   * budget}.
   */
  int fittingBefore(int end, int maxWords, int budget) {
    int limit = Math.min(maxWords, end);
    int count = 0;
    int left = budget;
    while (count < limit && costs[end - 1 - count] <= left) {
      left -= costs[end - 1 - count];
      count++;
    }
    return count;
  }
}
