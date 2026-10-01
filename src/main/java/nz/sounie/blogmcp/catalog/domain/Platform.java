package nz.sounie.blogmcp.catalog.domain;

import java.util.Arrays;
import java.util.Optional;

/** The blogging software behind a site. */
public enum Platform {
  WORDPRESS,
  BLOGGER;

  /** The platform with this name, ignoring case; empty for an unknown or missing name. */
  public static Optional<Platform> named(String name) {
    return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
  }
}
