package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.Map;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;

/** How a platform encodes a title: literal text, or markup to be turned into plain text. */
enum TitleFormat {
  TEXT {
    @Override
    String toPlainText(String raw, HtmlToText htmlToText) {
      return raw;
    }
  },
  HTML {
    @Override
    String toPlainText(String raw, HtmlToText htmlToText) {
      return htmlToText.extract(raw);
    }
  };

  private static final Map<String, TitleFormat> BY_ATOM_TYPE =
      Map.of("text", TEXT, "html", HTML, "xhtml", HTML);

  abstract String toPlainText(String raw, HtmlToText htmlToText);

  /**
   * The format named by an Atom {@code type} attribute. An unknown type is text; callers supply
   * {@code "text"} for an absent type.
   */
  static TitleFormat ofAtomType(String type) {
    return BY_ATOM_TYPE.getOrDefault(type, TEXT);
  }
}
