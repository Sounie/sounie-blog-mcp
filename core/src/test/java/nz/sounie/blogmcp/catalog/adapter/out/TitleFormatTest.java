package nz.sounie.blogmcp.catalog.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TitleFormatTest {

  private final MarkingHtmlToText htmlToText = new MarkingHtmlToText();

  @ParameterizedTest
  @CsvSource({"text, TEXT", "html, HTML", "xhtml, HTML"})
  void atom_types_name_their_format(String type, TitleFormat expected) {
    assertThat(TitleFormat.ofAtomType(type)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "markdown"})
  void an_empty_or_unknown_atom_type_is_text(String type) {
    assertThat(TitleFormat.ofAtomType(type)).isEqualTo(TitleFormat.TEXT);
  }

  @Test
  void text_is_kept_exactly_as_written() {
    assertThat(TitleFormat.TEXT.toPlainText("Generics: List<String> & co", htmlToText))
        .isEqualTo("Generics: List<String> & co");
  }

  @Test
  void html_goes_through_text_extraction() {
    assertThat(TitleFormat.HTML.toPlainText("Hello <em>World</em>", htmlToText))
        .isEqualTo("extracted(Hello <em>World</em>)");
  }
}
