package nz.sounie.blogmcp.search.adapter.out;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds index files as docs/domain/app.md 3.3 specifies them, independently of the production
 * code, so tests can plant valid, corrupt and foreign-model files. Vectors are encoded here with
 * the JDK directly (base64 of little-endian float32), not with {@link VectorCodec}.
 */
final class IndexFileJson {

  static final JsonMapper JSON = JsonMapper.builder().build();

  private IndexFileJson() {}

  static ObjectNode of(IndexedPost post, String modelId, String recipe) {
    ObjectNode root = JSON.createObjectNode();
    root.put("format", 1);
    root.put("postId", post.id().external());
    root.put("modelId", modelId);
    root.put("recipe", recipe);
    root.put("fingerprint", post.fingerprint().value());
    ObjectNode metadata = root.putObject("metadata");
    metadata.put("siteId", post.metadata().siteId().value());
    metadata.put("canonicalUrl", post.metadata().canonicalUrl());
    metadata.put("title", post.metadata().title());
    ArrayNode tags = metadata.putArray("tags");
    post.metadata().tags().forEach(tags::add);
    metadata.put("publishedAt", post.metadata().publishedAt().toString());
    metadata.put("updatedAt", post.metadata().updatedAt().toString());
    ArrayNode chunks = root.putArray("chunks");
    post.chunks().forEach(chunk -> addChunk(chunks, chunk));
    return root;
  }

  private static void addChunk(ArrayNode chunks, IndexedChunk chunk) {
    ObjectNode node = chunks.addObject();
    node.put("index", chunk.index());
    node.put("text", chunk.text());
    node.put("vector", vector(chunk.embedding().values()));
  }

  /** Base64 of the values as little-endian IEEE-754 float32. */
  static String vector(float[] values) {
    ByteBuffer bytes =
        ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    for (float value : values) {
      bytes.putFloat(value);
    }
    return Base64.getEncoder().encodeToString(bytes.array());
  }

  static ObjectNode chunk(ObjectNode file, int position) {
    return (ObjectNode) file.get("chunks").get(position);
  }

  static byte[] bytes(ObjectNode file) {
    return JSON.writeValueAsBytes(file);
  }
}
