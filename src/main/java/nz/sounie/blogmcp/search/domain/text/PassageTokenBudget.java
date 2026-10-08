package nz.sounie.blogmcp.search.domain.text;

/** Provider-selected token limits for chunk bodies and the fully prepared passage. */
public record PassageTokenBudget(
    int bodyTokens,
    int overlapTokens,
    int titleTokens,
    int preparedPassageTokens,
    int supportedContextTokens) {

  public void validate() {
    throw new UnsupportedOperationException("Passage token budget validation is not implemented");
  }}
