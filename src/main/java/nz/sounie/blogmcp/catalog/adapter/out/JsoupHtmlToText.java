package nz.sounie.blogmcp.catalog.adapter.out;

import nz.sounie.blogmcp.catalog.domain.HtmlToText;

/** Text extraction with jsoup. */
public final class JsoupHtmlToText implements HtmlToText {

  @Override
  public String extract(String html) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public String decodeEntities(String text) {
    throw new UnsupportedOperationException("not implemented");
  }
}
