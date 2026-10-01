package nz.sounie.blogmcp.search.domain;

/** The number of posts to return, clamped to 1..20; 10 when absent. */
public record ResultLimit(int value) {

  public static final int MIN = 1;
  public static final int MAX = 20;
  public static final int DEFAULT = 10;

  public static ResultLimit of(int requested) {
    throw new UnsupportedOperationException("not implemented");
  }

  public static ResultLimit defaultLimit() {
    throw new UnsupportedOperationException("not implemented");
  }
}
