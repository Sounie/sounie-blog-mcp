package nz.sounie.blogmcp.catalog.adapter.out;

/** Wraps its input in a marker, so tests can see which extraction each field went through. */
final class MarkingHtmlToText implements HtmlToText {

  @Override
  public String extract(String html) {
    return "extracted(" + html + ")";
  }

  @Override
  public String decodeEntities(String text) {
    return "decoded(" + text + ")";
  }
}
