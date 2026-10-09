package nz.sounie.blogmcp.search.domain.text;

/** Provider-neutral strategy for preparing a canonical title and chunk for indexing. */
@FunctionalInterface
public interface PassageFormatter {

  Passage format(String title, String content);

  static PassageFormatter standard() {
    return (title, content) ->
        new Passage(
            java.util.stream.Stream.of(title, content)
                .filter(part -> !part.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n")));
  }
}
