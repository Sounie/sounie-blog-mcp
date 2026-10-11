package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2;

import nz.sounie.blogmcp.search.domain.query.QueryText;

/** EmbeddingGemma 2 formatting for validated query text and canonical passage content. */
public final class Gemma2InputPreparation {

  private Gemma2InputPreparation() {}

  public static String gemma2Query(QueryText query) {
    return "task: search result | query: " + query.value();
  }

  public static String gemma2Passage(String title, String content) {
    String effectiveTitle = title == null || title.isBlank() ? "none" : title;
    return "title: " + effectiveTitle + " | text: " + content;
  }
}
