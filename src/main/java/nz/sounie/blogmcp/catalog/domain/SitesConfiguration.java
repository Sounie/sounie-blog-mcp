package nz.sounie.blogmcp.catalog.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.SitesConfigurationViolation.Kind;

/** The validated set of all sites. Valid only as a whole. */
public final class SitesConfiguration {

  private final List<Site> sites;

  private SitesConfiguration(List<Site> sites) {
    this.sites = List.copyOf(sites);
  }

  /**
   * Validates the whole list and reports every violation together.
   *
   * @throws InvalidSitesConfiguration listing all violations
   */
  public static SitesConfiguration of(List<SiteDefinition> definitions) {
    return new Validation(definitions).result();
  }

  /** Sites in configuration order. */
  public List<Site> sites() {
    return sites;
  }

  public Optional<Site> find(SiteId id) {
    return sites.stream().filter(site -> site.id().equals(id)).findFirst();
  }

  public Set<SiteId> siteIds() {
    Set<SiteId> ids = new LinkedHashSet<>();
    sites.forEach(site -> ids.add(site.id()));
    return Collections.unmodifiableSet(ids);
  }

  /** Checks every definition, collecting all violations before deciding. */
  private static final class Validation {

    private final List<SiteDefinition> definitions;
    private final List<SitesConfigurationViolation> violations = new ArrayList<>();
    private final List<Site> sites = new ArrayList<>();
    private final Set<String> seenIds = new HashSet<>();
    private final Set<String> seenBaseUrls = new HashSet<>();

    Validation(List<SiteDefinition> definitions) {
      this.definitions = definitions;
    }

    SitesConfiguration result() {
      if (definitions.isEmpty()) {
        violate(Kind.NO_SITES, "at least one site must be configured");
      }
      definitions.forEach(this::check);
      if (!violations.isEmpty()) {
        throw new InvalidSitesConfiguration(violations);
      }
      return new SitesConfiguration(sites);
    }

    private void check(SiteDefinition definition) {
      Optional<SiteId> id = siteId(definition.id());
      Optional<Platform> platform = platform(definition.platform());
      Optional<URI> baseUrl = baseUrl(definition.baseUrl());
      if (id.isPresent() && platform.isPresent() && baseUrl.isPresent()) {
        sites.add(new Site(id.get(), platform.get(), baseUrl.get()));
      }
    }

    private Optional<SiteId> siteId(String id) {
      if (!SiteId.isValid(id)) {
        violate(Kind.INVALID_SITE_ID, "site ID must match [a-z0-9-]{1,40}: '" + id + "'");
        return Optional.empty();
      }
      if (!seenIds.add(id)) {
        violate(Kind.DUPLICATE_SITE_ID, "site ID used more than once: " + id);
        return Optional.empty();
      }
      return Optional.of(new SiteId(id));
    }

    private Optional<Platform> platform(String name) {
      Optional<Platform> platform = Platform.named(name);
      if (platform.isEmpty()) {
        violate(Kind.UNSUPPORTED_PLATFORM, "unsupported platform: '" + name + "'");
      }
      return platform;
    }

    private Optional<URI> baseUrl(String text) {
      Optional<URI> parsed = parse(text);
      if (parsed.isEmpty()) {
        violate(Kind.BASE_URL_NOT_ABSOLUTE_HTTPS, "base URL is not a URL: '" + text + "'");
        return Optional.empty();
      }
      URI baseUrl = parsed.get();
      Optional<Kind> problem = Site.baseUrlProblem(baseUrl);
      if (problem.isPresent()) {
        violate(problem.get(), "invalid base URL: " + text);
        return Optional.empty();
      }
      if (!seenBaseUrls.add(comparable(baseUrl))) {
        violate(Kind.DUPLICATE_BASE_URL, "base URL used more than once: " + text);
        return Optional.empty();
      }
      return parsed;
    }

    private static Optional<URI> parse(String text) {
      if (text == null) {
        return Optional.empty();
      }
      try {
        return Optional.of(new URI(text));
      } catch (URISyntaxException e) {
        return Optional.empty();
      }
    }

    /** Base URLs that differ only in host case or a trailing slash are the same site. */
    private static String comparable(URI baseUrl) {
      String path = baseUrl.getRawPath();
      String withoutTrailingSlash =
          path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
      return baseUrl.getHost().toLowerCase(Locale.ROOT)
          + ":"
          + baseUrl.getPort()
          + withoutTrailingSlash;
    }

    private void violate(Kind kind, String detail) {
      violations.add(new SitesConfigurationViolation(kind, detail));
    }
  }
}
