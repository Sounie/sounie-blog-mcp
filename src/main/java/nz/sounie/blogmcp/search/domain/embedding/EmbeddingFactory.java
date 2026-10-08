package nz.sounie.blogmcp.search.domain.embedding;

public class EmbeddingFactory {
    public static Embedding createOnnxEmbedding(float[] values) {
        return new Embedding(384, values);
    }

    public static Embedding createGemma2Embedding(float[] values) {
        return new Embedding( 768, values);
    }
}
