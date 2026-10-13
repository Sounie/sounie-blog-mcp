package nz.sounie.blogmcp.search.adapter.out;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** What an image response's status means for the fetch (search.md 3.7). */
enum FetchStatus {
  /** 2xx: read the body. */
  SUCCESS,
  /** 3xx with a {@code Location}: follow it, if redirects are left. */
  REDIRECT,
  /** 408, 429 or 5xx: worth retrying. */
  TRANSIENT,
  /** Any other status: the image can never be fetched. */
  PERMANENT;

  private static final Set<Integer> TRANSIENT_CLIENT_ERRORS = Set.of(408, 429);
  private static final int SERVER_ERRORS = 500;

  private record Rule(Predicate<HttpResponse<?>> matches, FetchStatus status) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule(response -> statusClass(response) == 2, SUCCESS),
          new Rule(response -> statusClass(response) == 3 && hasLocation(response), REDIRECT),
          new Rule(response -> TRANSIENT_CLIENT_ERRORS.contains(response.statusCode()), TRANSIENT),
          new Rule(response -> response.statusCode() >= SERVER_ERRORS, TRANSIENT));

  /** The first rule that matches decides; anything else is {@link #PERMANENT}. */
  static FetchStatus of(HttpResponse<?> response) {
    return RULES.stream()
        .filter(rule -> rule.matches().test(response))
        .map(Rule::status)
        .findFirst()
        .orElse(PERMANENT);
  }

  private static boolean hasLocation(HttpResponse<?> response) {
    return response.headers().firstValue("Location").isPresent();
  }

  private static int statusClass(HttpResponse<?> response) {
    return response.statusCode() / 100;
  }
}
