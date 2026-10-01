package nz.sounie.blogmcp.catalog.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsoupHtmlToTextTest {

  private final JsoupHtmlToText htmlToText = new JsoupHtmlToText();

  @Test
  @DisplayName("AC-CAT-6: HTML becomes plain text")
  void extracts_paragraphs_keeps_pre_whitespace_and_drops_scripts() {
    String text =
        htmlToText.extract("<p>One&nbsp;line</p><script>x()</script><pre>  a\n  b</pre><p>Two</p>");

    assertThat(text).isEqualTo("One line\n\n  a\n  b\n\nTwo");
  }

  @Test
  void drops_style_elements() {
    assertThat(htmlToText.extract("<style>p { color: red; }</style><p>Visible</p>"))
        .isEqualTo("Visible");
  }

  @Test
  void collapses_whitespace_outside_pre() {
    assertThat(htmlToText.extract("<p>a   \n\t  b</p>")).isEqualTo("a b");
  }

  @Test
  void keeps_inline_elements_inside_a_paragraph() {
    assertThat(htmlToText.extract("<p>Read <a href=\"/x\">the <em>docs</em></a> first.</p>"))
        .isEqualTo("Read the docs first.");
  }

  @Test
  void separates_headings_and_list_items_with_paragraph_breaks() {
    assertThat(htmlToText.extract("<h2>Title</h2><ul><li>one</li><li>two</li></ul>"))
        .isEqualTo("Title\n\none\n\ntwo");
  }

  @Test
  void decodes_entities_in_text() {
    assertThat(htmlToText.extract("<p>Fish &amp; chips &#8211; &lt;cheap&gt;</p>"))
        .isEqualTo("Fish & chips – <cheap>");
  }

  @Test
  void empty_html_gives_empty_text() {
    assertThat(htmlToText.extract("")).isEmpty();
  }

  @Test
  @DisplayName("AC-CAT-6: entities in titles are decoded")
  void decodes_entities_in_a_title() {
    assertThat(htmlToText.decodeEntities("Don&#8217;t &amp; &lt;panic&gt;"))
        .isEqualTo("Don’t & <panic>");
  }

  @Test
  void decoding_leaves_plain_text_alone() {
    assertThat(htmlToText.decodeEntities("Plain title")).isEqualTo("Plain title");
  }
}
