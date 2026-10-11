package nz.sounie.blogmcp.search.domain.text;

import java.util.List;
import java.util.stream.IntStream;

/** Numbered words {@code w1 w2 ...} for chunking tests. */
public final class Words {

  private Words() {}

  /** {@code w<from>..w<to>}, inclusive. */
  public static List<String> range(int from, int to) {
    return IntStream.rangeClosed(from, to).mapToObj(i -> "w" + i).toList();
  }

  /** A body of {@code w1..w<n>} separated by single spaces. */
  public static String numbered(int n) {
    return String.join(" ", range(1, n));
  }

  /** A body of {@code n} words with a prefix, e.g. {@code t1 t2 ...}. */
  public static String numbered(String prefix, int n) {
    return String.join(" ", IntStream.rangeClosed(1, n).mapToObj(i -> prefix + i).toList());
  }
}
