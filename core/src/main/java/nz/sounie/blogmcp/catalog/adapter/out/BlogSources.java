package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.http.HttpClient;
import java.util.Map;
import nz.sounie.blogmcp.catalog.application.BlogSource;
import nz.sounie.blogmcp.catalog.domain.site.Platform;

/** The production blog sources, one per platform, for the composition root. */
public final class BlogSources {

  private BlogSources() {}

  /** WordPress and Blogger over one JDK HTTP client, with jsoup text extraction. */
  public static Map<Platform, BlogSource> overHttp() {
    HttpClient http = JsonHttp.defaultClient();
    HtmlToText htmlToText = new JsoupHtmlToText();
    return Map.of(
        Platform.WORDPRESS, new WordPressSource(http, htmlToText),
        Platform.BLOGGER, new BloggerSource(http, htmlToText));
  }
}
