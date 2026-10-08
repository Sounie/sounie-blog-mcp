package nz.sounie.blogmcp.search.adapter.out;

import nz.sounie.blogmcp.search.domain.query.QueryText;

/** Provider-specific formatting for validated query text and canonical passage content. */
final class EmbeddingInputPreparation {

  private EmbeddingInputPreparation() {}

  static String onnxQuery(QueryText query) {
    throw new UnsupportedOperationException("ONNX query preparation is not implemented");
  }

  static String onnxPassage(String canonicalPassage) {
    throw new UnsupportedOperationException("ONNX passage preparation is not implemented");
  }

  static String gemma2Query(QueryText query) {
    throw new UnsupportedOperationException("Gemma2 query preparation is not implemented");
  }

  static String gemma2Passage(String title, String content) {
    throw new UnsupportedOperationException("Gemma2 passage preparation is not implemented");
  }
}
