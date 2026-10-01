package nz.sounie.blogmcp.search.domain;

/** What an index decision is applied with: the vector index and the post indexer. */
public record IndexWork(VectorIndex index, PostIndexer indexer) {}
