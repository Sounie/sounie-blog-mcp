# 11. Image embeddings for Gemma2

Date: 2026-10-13

## Status

Proposed. It becomes Accepted once the measurements under [Parity](#parity) are filled in from the
official-weights reference run (`scripts/gemma2-image-reference.py`) and the owner accepts the
thresholds. It extends ADR 0010 and leaves ADR 0009's SPI decisions unchanged.

## Context

`search_posts` only sees text. `JsoupHtmlToText` drops every `<img>`, so a post whose point is a
photo can only be found through its words.

EmbeddingGemma 2 is multimodal: text, images, video and audio map into one 768-dimensional space.
The spike (PR 0) embedded six test images with the q8 ONNX graphs and ran one text query per image,
using the production query prompt. All six queries ranked their intended image first, with cosines
of 0.673 to 0.783.

The owner wants two features:
1. **Index post images.** Images that are part of a post, and not part of navigation or site
   headers, are indexed so that text queries can match what they show.
2. **Search by image.** A user gives an image as a local file path or an HTTPS URL. The search
   returns the posts whose text or images are most similar to it.

**Research findings (2026-10-13):**
- `google/embeddinggemma-2` is Apache-2.0.
  - Its default image budget is 280 soft tokens. The image processor (`Gemma4ImageProcessor`) uses
    16 px patches and 3×3 pooling.
  - Images take no task prefix; text keeps its prefixes.
  - Image and text can be interleaved in one input.
- **Official runtimes** are transformers, sentence-transformers and transformers.js. None of them is
  Java.
- **No host offers an image embedding we can use.**
  - There is no hosted API for EmbeddingGemma 2.
  - The GGUF build used by llama.cpp is text-only, with no mmproj file.
  - LM Studio's `/v1/embeddings` has no documented image support.
  - Ollama lists "Image" for some tags but documents no way to send one.
- **`onnx-community/embeddinggemma-2-ONNX`** ships `vision_encoder` at fp32 (671 MB), fp16, q8
  (195 MB) and q4.
  - Its worst-case cosine against fp32 is 0.9997 for q8 and 0.975 for q4.
  - The model card warns that fp16 can produce NaN.
  - The q8 vision graph takes `pixel_values [1, 2520, 768]` and `pixel_position_ids`, and outputs
    `image_features [n, 512]`. The text graph takes these features through its `image_features`
    input.

## Decision

1. **The official weights are the reference.**
   - `scripts/gemma2-image-reference.py` runs `google/embeddinggemma-2` at `914f7f89…` in fp32
     PyTorch, with mean pooling and L2 normalisation.
   - It records `core/src/test/resources/gemma2-image-reference.json`, which holds the input ids, the
     patch grid, the pixel sum and the embedding for each fixture image and query.
   - The Java implementation is measured against that file, not against ONNX output.
2. **The runtime is ONNX Runtime Java in the in-process `gemma2` provider.**
   - It uses `vision_encoder_quantized` (q8), matching the q8 text model ADR 0010 already bundles.
   - The file is pinned through the Hugging Face Ivy repository, and its checksums are updated
     through the verification-metadata script.
   - The fp32 encoder is the fallback, used only if q8 misses the parity threshold.
   - `gemma2-server` and `onnx` (BGE) stay text-only.
3. **Preprocessing is ported to Java from `image_processing_pil_gemma4.py`.**
   - Size: scale both sides to fit 280×9 patches of 16×16, then floor each to a multiple of 48.
   - Resize with a port of PIL's antialiased bicubic convolution. `AffineTransformOp` is a different
     filter.
   - Rescale by 1/255, with no normalisation.
   - Split into patches with `(x, y)` position ids. Pad to 2,520 positions: pixels with 0, positions
     with −1.
   - The input ids are `[2, 255999, 258880×n, 258882, 1]`.
4. **Providers declare image capability.** `EmbeddingProvider` exposes an optional image embedder.
   Providers without one index text only and don't offer search by image.
5. **Only post-body images are indexed.**
   - Images come from the post content field of the platform API: WordPress `content.rendered` and
     Blogger `content.$t`. Those fields never contain navigation, headers or sidebars.
   - Emoji, tiny images, tracking pixels, `data:` URIs, non-HTTPS URLs and duplicates are excluded.
6. **Each image is embedded alone, as its own index entry beside the post's text chunks.**
   - A post scores as its best entry.
   - An image match's snippet is the image's alt text, then its caption, then its URL.
7. **Image fetching is untrusted input.**
   - Only HTTPS from the configured sites' hosts and their platform media CDNs.
   - At most 10 MB, with content-type checks and timeouts.
   - A failed image skips only that image.
8. **Search by image is a separate MCP tool, `find_posts_like_image`.**
   - Local paths must be regular image files of at most 10 MB.
   - URLs must be HTTPS and must not resolve to loopback, link-local or private addresses.
   - The tool returns only scores and snippets.

## Parity

To be filled in from the official-weights reference run:

| Measure | Value | Threshold |
|---|---|---|
| Query embeddings, q8 ONNX vs official | _pending_ | ≥ 0.999 (validates the pooling) |
| Image embeddings, q8 ONNX (Python preprocessing) vs official | _pending_ | _set from measurement_ |
| Image embeddings, Java vs official | measured in PR 1 | _set from measurement_ |
| Queries ranking their intended image first (official) | _pending_ | 6 of 6 |

## Consequences

- The `gemma2` jar grows by about 195 MB.
- The image recipe joins the index recipe, so enabling images re-embeds every post once.
- Each image costs one vision-encoder pass, then about 270 tokens through the text model.
- The catalog's stored post format and the search index file format both move to version 2.
- Search quality for images depends on alt text only for snippets, not for matching.

## Delivery

Delivered in five phased PRs. The phases, their scope and their status are tracked in
[docs/plans/image-embeddings.md](../plans/image-embeddings.md).
