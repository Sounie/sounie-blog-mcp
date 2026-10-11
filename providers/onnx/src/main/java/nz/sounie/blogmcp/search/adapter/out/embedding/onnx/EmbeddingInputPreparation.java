package nz.sounie.blogmcp.search.adapter.out.embedding.onnx;

import nz.sounie.blogmcp.search.domain.query.QueryText;

/** ONNX BGE formatting for validated query text and canonical passage content. */
final class EmbeddingInputPreparation {

  private EmbeddingInputPreparation() {}

  static String onnxQuery(QueryText query) {
    return "Represent this sentence for searching relevant passages: " + query.value();
  }

  static String onnxPassage(String canonicalPassage) {
    return canonicalPassage;
  }
}
