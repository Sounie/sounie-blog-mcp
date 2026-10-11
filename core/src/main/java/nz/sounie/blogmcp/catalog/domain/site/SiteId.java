package nz.sounie.blogmcp.catalog.domain.site;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Owner-chosen, stable, lower-case slug that identifies a site: {@code [a-z0-9-]{1,40}}. Rejects
 * anything else with {@link IllegalArgumentException}.
 */
public record SiteId(String value) {

  private static final Pattern SLUG = Pattern.compile("[a-z0-9-]{1,40}");

  public SiteId {
    Objects.requireNonNull(value, "site ID");
    if (!isValid(value)) {
      throw new IllegalArgumentException("Site ID must match [a-z0-9-]{1,40}: '" + value + "'");
    }
  }

  /** Whether the text is a valid site ID. */
  public static boolean isValid(String value) {
    return value != null && SLUG.matcher(value).matches();
  }
}
