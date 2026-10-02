package nz.sounie.blogmcp.search.adapter.out;

import java.util.function.ToIntFunction;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/** Deterministic fake of our {@link TokenCounter} port. */
public final class FakeTokenCounter implements TokenCounter {

  private final ToIntFunction<String> cost;

  private FakeTokenCounter(ToIntFunction<String> cost) {
    this.cost = cost;
  }

  /** Every word costs the same number of tokens. */
  public static FakeTokenCounter perWord(int tokens) {
    return new FakeTokenCounter(word -> tokens);
  }

  /** One token per character: the WordPiece worst case. */
  public static FakeTokenCounter perCharacter() {
    return new FakeTokenCounter(String::length);
  }

  /** Each word costs 1 to 9 tokens, chosen by its hash: irregular but deterministic. */
  public static FakeTokenCounter irregular() {
    return new FakeTokenCounter(word -> 1 + Math.floorMod(word.hashCode(), 9));
  }

  @Override
  public int tokensIn(String word) {
    return cost.applyAsInt(word);
  }
}
