package nz.sounie.blogmcp.catalog.domain.site;

import java.net.URI;

/** Sites used across catalog tests. */
public final class TestSites {

  public static final SiteId SOUNIE_WP_ID = new SiteId("sounie-wp");
  public static final SiteId ELEGANT_ID = new SiteId("elegant");

  public static final Site SOUNIE_WP =
      new Site(SOUNIE_WP_ID, Platform.WORDPRESS, URI.create("https://blog2.sounie.nz"));
  public static final Site ELEGANT =
      new Site(ELEGANT_ID, Platform.BLOGGER, URI.create("https://blog.elegant-solutions.london"));

  public static final SiteDefinition SOUNIE_WP_DEFINITION =
      new SiteDefinition("sounie-wp", "WORDPRESS", "https://blog2.sounie.nz");
  public static final SiteDefinition ELEGANT_DEFINITION =
      new SiteDefinition("elegant", "BLOGGER", "https://blog.elegant-solutions.london");

  private TestSites() {}
}
