package nz.sounie.blogmcp.search.domain;

/** Port: how many model tokens one word costs. Additive over words. */
public interface TokenCounter {

  int tokensIn(String word);
}
