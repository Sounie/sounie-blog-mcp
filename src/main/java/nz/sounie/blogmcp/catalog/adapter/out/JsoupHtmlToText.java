package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

/** Text extraction with jsoup. */
public final class JsoupHtmlToText implements HtmlToText {

  private static final String PARAGRAPH_BREAK = "\n\n";
  private static final char NO_BREAK_SPACE = (char) 0x00a0;

  /** Stands in for a {@code <br>} until whitespace has been collapsed. */
  private static final char LINE_BREAK_MARK = (char) 0x2028; // LINE SEPARATOR

  @Override
  public String extract(String html) {
    Blocks blocks = new Blocks();
    blocks.walk(Jsoup.parseBodyFragment(html).body());
    return blocks.text();
  }

  @Override
  public String decodeEntities(String text) {
    return Parser.unescapeEntities(text, false).replace(NO_BREAK_SPACE, ' ');
  }

  /** Collects the text of block elements, each of which ends in a paragraph break. */
  private static final class Blocks {

    private final List<String> blocks = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();

    void walk(Element parent) {
      for (Node child : parent.childNodes()) {
        switch (child) {
          case TextNode text -> current.append(text.getWholeText());
          case Element element -> visit(element);
          default -> {
            // comments, doctype and data nodes carry no visible text
          }
        }
      }
    }

    private void visit(Element element) {
      ElementHandling.of(element.normalName()).apply(this, element);
    }

    private void lineBreak() {
      current.append(LINE_BREAK_MARK);
    }

    private void block(Element element) {
      endBlock();
      walk(element);
      endBlock();
    }

    private void preformatted(Element element) {
      endBlock();
      addPreformatted(element.wholeText());
    }

    private void addPreformatted(String text) {
      String kept = text.replace(NO_BREAK_SPACE, ' ').stripTrailing().replaceFirst("^\n+", "");
      if (!kept.isBlank()) {
        blocks.add(kept);
      }
    }

    private void endBlock() {
      String collapsed =
          current
              .toString()
              .replace(NO_BREAK_SPACE, ' ')
              .replaceAll("\\s+", " ")
              .replaceAll(" ?" + LINE_BREAK_MARK + " ?", "\n")
              .strip();
      current.setLength(0);
      if (!collapsed.isEmpty()) {
        blocks.add(collapsed);
      }
    }

    String text() {
      endBlock();
      return String.join(PARAGRAPH_BREAK, blocks);
    }
  }

  /** What an element contributes to the text, looked up by element name. */
  private enum ElementHandling {
    /** Scripts, styles and the like carry no visible text. */
    DROP {
      @Override
      void apply(Blocks blocks, Element element) {
        // nothing visible
      }
    },
    LINE_BREAK {
      @Override
      void apply(Blocks blocks, Element element) {
        blocks.lineBreak();
      }
    },
    /** Whitespace is kept exactly. */
    PREFORMATTED {
      @Override
      void apply(Blocks blocks, Element element) {
        blocks.preformatted(element);
      }
    },
    /** Ends in a paragraph break. */
    BLOCK {
      @Override
      void apply(Blocks blocks, Element element) {
        blocks.block(element);
      }
    },
    /** Part of the surrounding text. */
    INLINE {
      @Override
      void apply(Blocks blocks, Element element) {
        blocks.walk(element);
      }
    };

    private static final Map<String, ElementHandling> BY_NAME = byName();

    abstract void apply(Blocks blocks, Element element);

    static ElementHandling of(String elementName) {
      return BY_NAME.getOrDefault(elementName, INLINE);
    }

    private static Map<String, ElementHandling> byName() {
      Map<String, ElementHandling> byName = new HashMap<>();
      Stream.of("script", "style", "noscript", "template").forEach(name -> byName.put(name, DROP));
      Stream.of(
              "address",
              "article",
              "aside",
              "blockquote",
              "dd",
              "details",
              "div",
              "dl",
              "dt",
              "figcaption",
              "figure",
              "footer",
              "form",
              "h1",
              "h2",
              "h3",
              "h4",
              "h5",
              "h6",
              "header",
              "hr",
              "li",
              "main",
              "nav",
              "ol",
              "p",
              "section",
              "summary",
              "table",
              "tbody",
              "td",
              "tfoot",
              "th",
              "thead",
              "tr",
              "ul")
          .forEach(name -> byName.put(name, BLOCK));
      byName.put("br", LINE_BREAK);
      byName.put("pre", PREFORMATTED);
      return Map.copyOf(byName);
    }
  }
}
