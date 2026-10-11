package nz.sounie.blogmcp.search.domain.text;

/** Provider-selected token limits for chunk bodies and the fully prepared passage. */
public record PassageTokenBudget(
    int bodyTokens,
    int overlapTokens,
    int titleTokens,
    int preparedPassageTokens,
    int supportedContextTokens) {

  public PassageTokenBudget {
    if (bodyTokens < 1) {
      throw new IllegalArgumentException("bodyTokens must be positive: " + bodyTokens);
    }
    if (overlapTokens < 0) {
      throw new IllegalArgumentException("overlapTokens must be >= 0: " + overlapTokens);
    }
    if (titleTokens < 0) {
      throw new IllegalArgumentException("titleTokens must be >= 0: " + titleTokens);
    }
    if (preparedPassageTokens < 1) {
      throw new IllegalArgumentException(
          "preparedPassageTokens must be positive: " + preparedPassageTokens);
    }
    if (supportedContextTokens < 1) {
      throw new IllegalArgumentException(
          "supportedContextTokens must be positive: " + supportedContextTokens);
    }
    if (overlapTokens >= bodyTokens) {
      throw new IllegalArgumentException(
          "overlapTokens must be less than bodyTokens: " + overlapTokens + " >= " + bodyTokens);
    }
    if (titleTokens > preparedPassageTokens) {
      throw new IllegalArgumentException(
          "titleTokens must be <= preparedPassageTokens: "
              + titleTokens
              + " > "
              + preparedPassageTokens);
    }
    if (preparedPassageTokens > supportedContextTokens) {
      throw new IllegalArgumentException(
          "preparedPassageTokens must be <= supportedContextTokens: "
              + preparedPassageTokens
              + " > "
              + supportedContextTokens);
    }
  }

  public void validate() {
    // Compact constructor already validates any new instance.
  }
}
