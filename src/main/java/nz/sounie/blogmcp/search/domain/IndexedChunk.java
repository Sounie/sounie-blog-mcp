package nz.sounie.blogmcp.search.domain;

/** A chunk's index and text, with its embedding. */
public record IndexedChunk(int index, String text, Embedding embedding) {}
