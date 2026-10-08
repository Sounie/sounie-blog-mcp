package nz.sounie.blogmcp.search.adapter.out;

import nz.sounie.blogmcp.search.domain.query.QueryText;

/** Provider-specific formatting for validated query text and canonical passage content. */
final class EmbeddingInputPreparation {

  private EmbeddingInputPreparation() {}

  static String onnxQuery(QueryText query) {
    return "Represent this sentence for searching relevant passages: " + query.value();
  }

  static String onnxPassage(String canonicalPassage) {
    return canonicalPassage;
  }

  static String gemma2Query(QueryText query) {
    return "task: search result | query: " + query.value();
  }

  static String gemma2Passage(String title, String content) {
    String effectiveTitle = title == null || title.isBlank() ? "none" : title;
    return "title: " + effectiveTitle + " | text: " + content;
  }
}
