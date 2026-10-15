package nz.sounie.blogmcp.search.domain.image;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import nz.sounie.blogmcp.search.domain.image.InvalidImageQuery.Reason;

/**
 * The rules an image query must meet (search.md 3.8), each a row of a table checked in order; the
 * first one broken decides the {@link InvalidImageQuery}. The local rules touch the JDK's {@code
 * Files}, so they are tested against real files.
 */
final class QueryImageRules {

  /** A scheme followed by {@code ://}: the text is meant as a URL (as catalog's PostReference). */
  static final Pattern URL_START = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://");

  private static final Pattern IMAGE_FILE_NAME =
      Pattern.compile(".+\\.(jpg|jpeg|png|gif)", Pattern.CASE_INSENSITIVE);

  private static final String HTTPS = "https";

  /** One rule: when it is broken, the reason and the message about the offending value. */
  private record Rule<T>(Predicate<T> broken, Reason reason, Function<T, String> message) {

    Optional<InvalidImageQuery> check(T value) {
      return Optional.of(value)
          .filter(broken)
          .map(offending -> new InvalidImageQuery(reason, message.apply(offending)));
    }
  }

  private static final List<Rule<Path>> LOCAL_RULES =
      List.of(
          new Rule<>(
              path -> !path.isAbsolute(),
              Reason.NOT_ABSOLUTE_PATH,
              path -> notAbsolute(path.toString())),
          new Rule<>(
              path -> !hasImageFileName(path),
              Reason.UNSUPPORTED_EXTENSION,
              path ->
                  "Cannot use image '" + path + "': it must be a .jpg, .jpeg, .png or .gif file"),
          new Rule<>(
              path -> !(Files.isRegularFile(path) && Files.isReadable(path)),
              Reason.NOT_A_READABLE_FILE,
              QueryImageRules::notReadable),
          new Rule<>(
              QueryImageRules::tooLarge,
              Reason.FILE_TOO_LARGE,
              path -> "Cannot use image '" + path + "': it is larger than 10 MB"));

  private static final List<Rule<URI>> REMOTE_RULES =
      List.of(
          new Rule<>(
              url -> !HTTPS.equalsIgnoreCase(url.getScheme()),
              Reason.NOT_HTTPS,
              url -> "Cannot use image '" + url + "': an image URL must use https"),
          new Rule<>(
              url -> Optional.ofNullable(url.getHost()).filter(host -> !host.isBlank()).isEmpty(),
              Reason.MALFORMED_URL,
              url -> malformed(url.toString())));

  private QueryImageRules() {}

  static void checkLocal(Path path) {
    firstBroken(LOCAL_RULES, path);
  }

  static void checkRemote(URI url) {
    firstBroken(REMOTE_RULES, url);
  }

  static String notAbsolute(String text) {
    return "Cannot use image '"
        + text
        + "': image must be an absolute path to a .jpg, .jpeg, .png or .gif file, or an https URL";
  }

  static String malformed(String text) {
    return "Cannot use image '" + text + "': it is not a valid URL with a host";
  }

  private static String notReadable(Path path) {
    return "Cannot use image: no readable file at '" + path + "'";
  }

  private static <T> void firstBroken(List<Rule<T>> rules, T value) {
    rules.stream()
        .flatMap(rule -> rule.check(value).stream())
        .findFirst()
        .ifPresent(
            rejection -> {
              throw rejection;
            });
  }

  private static boolean hasImageFileName(Path path) {
    return Optional.ofNullable(path.getFileName())
        .map(name -> IMAGE_FILE_NAME.matcher(name.toString()).matches())
        .orElse(false);
  }

  /** A file whose size cannot be read is not a readable file. */
  private static boolean tooLarge(Path path) {
    try {
      return Files.size(path) > ImageQuery.MAX_FILE_BYTES;
    } catch (IOException e) {
      throw new InvalidImageQuery(Reason.NOT_A_READABLE_FILE, notReadable(path));
    }
  }
}
