package nz.sounie.blogmcp.catalog.domain;

/**
 * Position of a page in a blog source's listing. 1-based; each source decides what it means (a
 * WordPress page number, a Blogger start-index).
 */
public record PageCursor(int value) {

  public static PageCursor first() {
    return new PageCursor(1);
  }
}
