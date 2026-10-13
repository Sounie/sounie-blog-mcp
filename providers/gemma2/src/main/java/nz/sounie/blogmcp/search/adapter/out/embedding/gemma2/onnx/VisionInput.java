package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

/**
 * What the vision encoder takes, flattened row-major: {@code pixelValues} is {@code pixel_values
 * [1, 2520, 768]} and {@code positionIds} is {@code pixel_position_ids [1, 2520, 2]} as (x, y)
 * pairs.
 */
record VisionInput(float[] pixelValues, long[] positionIds, PatchGrid grid) {}
