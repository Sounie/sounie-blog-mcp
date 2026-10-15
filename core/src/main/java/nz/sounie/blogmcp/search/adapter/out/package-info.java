/**
 * Outbound adapters for search: the vector index, the catalog query translation, fetching and
 * decoding post and query images, and the local model (the only code that touches LangChain4j, ONNX
 * Runtime and DJL; ADR 0005).
 */
package nz.sounie.blogmcp.search.adapter.out;
