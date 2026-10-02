package nz.sounie.blogmcp.search.domain;

/**
 * The number of posts to return, clamped to 1..20; 10 when absent. The compact constructor clamps,
 * so every result limit is in range.
 */
public record ResultLimit(int value) {

  public static final int MIN = 1;
  public static final int MAX = 20;
  public static final int DEFAULT = 10;

  public ResultLimit {
    value = Math.clamp(value, MIN, MAX);
  }

  public static ResultLimit of(int requested) {
    return new ResultLimit(requested);
  }

  public static ResultLimit defaultLimit() {
    return new ResultLimit(DEFAULT);
  }
}
