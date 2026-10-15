package nz.sounie.blogmcp.search.adapter.out;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.image.QueryImageSource;

/**
 * Fake of our {@link QueryImageSource} port: returns the {@link ImageFetch} scripted for each file
 * path or URL, and records every call. An unscripted path or URL is a mistake in the test, so it
 * fails loudly.
 */
public final class InMemoryQueryImageSource implements QueryImageSource {

  private final Map<Path, ImageFetch> files = new ConcurrentHashMap<>();
  private final Map<URI, ImageFetch> urls = new ConcurrentHashMap<>();
  private final List<Path> filesRead = new CopyOnWriteArrayList<>();
  private final List<URI> urlsFetched = new CopyOnWriteArrayList<>();

  @Override
  public ImageFetch readFile(Path path) {
    filesRead.add(path);
    ImageFetch fetch = files.get(path);
    if (fetch == null) {
      throw new IllegalStateException("No read scripted for " + path);
    }
    return fetch;
  }

  @Override
  public ImageFetch fetch(URI url) {
    urlsFetched.add(url);
    ImageFetch fetch = urls.get(url);
    if (fetch == null) {
      throw new IllegalStateException("No fetch scripted for " + url);
    }
    return fetch;
  }

  /** The file at this path reads as these pixels. */
  public InMemoryQueryImageSource reading(Path path, ImagePixels pixels) {
    files.put(path, new ImageFetch.Fetched(pixels));
    return this;
  }

  /** The image at this URL fetches as these pixels. */
  public InMemoryQueryImageSource fetching(String url, ImagePixels pixels) {
    return scripting(url, new ImageFetch.Fetched(pixels));
  }

  /** The image at this URL is rejected for this reason. */
  public InMemoryQueryImageSource rejecting(String url, ImageRejection reason) {
    return scripting(url, new ImageFetch.Rejected(reason));
  }

  /** The image at this URL is unavailable, with this detail. */
  public InMemoryQueryImageSource unavailable(String url, String detail) {
    return scripting(url, new ImageFetch.Unavailable(detail));
  }

  private InMemoryQueryImageSource scripting(String url, ImageFetch fetch) {
    urls.put(URI.create(url), fetch);
    return this;
  }

  public List<Path> filesRead() {
    return List.copyOf(filesRead);
  }

  public List<URI> urlsFetched() {
    return List.copyOf(urlsFetched);
  }
}
