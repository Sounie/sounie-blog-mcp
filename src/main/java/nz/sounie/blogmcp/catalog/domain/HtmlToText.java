package nz.sounie.blogmcp.catalog.domain;

/** Port: text extraction from platform HTML. Implemented in an adapter. */
public interface HtmlToText {

  /**
   * Drops {@code script}/{@code style}, decodes entities, puts a paragraph break after block
   * elements, keeps whitespace inside {@code pre}, and collapses other whitespace.
   */
  String extract(String html);

  /** Decodes HTML entities in plain text, e.g. a title. */
  String decodeEntities(String text);
}
