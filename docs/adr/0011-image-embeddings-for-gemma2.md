# 11. Image embeddings for Gemma2

Date: 2026-10-13

## Status

Accepted by the owner on 2026-10-13, with the [Parity](#parity) measurements and thresholds. It
extends ADR 0010 and leaves ADR 0009's SPI decisions unchanged.

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
   - Only HTTPS. *Amended by the owner on 2026-10-14:* there is no host allow-list, because blogs
     may serve images from arbitrary domains of their own. Images may come from any https host whose
     address category is permitted by the category of the post's host. A public blog permits only
     public image addresses, and a private-network blog also permits private-network addresses.
     Link-local, any-local and multicast addresses are fetched only for a blog whose own host is in
     the same non-routable category. The check is repeated for
     every redirect target. DNS rebinding is an accepted residual risk.
   - At most 10 MB, with content-type checks and timeouts.
   - A failed image skips only that image.
8. **Search by image is a separate MCP tool, `find_posts_like_image`.**
   - Local paths must be regular image files of at most 10 MB.
   - URLs must be HTTPS. *Amended by the owner on 2026-10-15:* while the server is stdio-only, a
     query URL may resolve to any address, including loopback, private and link-local ones, because
     the caller is the local user. The redirect limit, the size and content-type caps and the
     timeouts still apply. Revisit when the transport moves from stdio to HTTP. (Previously: "must
     not resolve to loopback, link-local or private addresses".)
   - The tool returns only scores and snippets.

## Parity

Measured on 2026-10-13 with `scripts/gemma2-image-reference.py`. It ran the official weights in
fp32 PyTorch (transformers 5.19) and the bundled q8 ONNX graphs on the same processor output, over
the six fixtures in `core/src/test/resources/`. Every image used 266 image tokens, 270 input ids
and 2,520 patches.

| Measure | Value | Threshold |
|---|---|---|
| Query embeddings, q8 ONNX vs official | 0.99995 to 0.99998 | ≥ 0.999 (validates the pooling) |
| Image embeddings, q8 ONNX (Python preprocessing) vs official | 0.99909 to 0.99981 | ≥ 0.999 |
| Image embeddings, Java vs official | 0.99908 to 0.99980 (phase 1, 2026-10-14; lowest `race-car-at-circuit.png`) | ≥ 0.994 (worst q8 result, 0.99909, minus 0.005 for resize drift) |
| Queries ranking their intended image first (official) | 6 of 6 | 6 of 6 |

Phase 1 note: the Java resize matches PIL's `Image.resize(BICUBIC)` byte for byte on all six
fixtures (measured on 2026-10-14 with a one-off PIL/torchvision script, not committed). The reference's `pixelValuesSum` matches torchvision's antialiased bicubic exactly, not
PIL's. So transformers 5.19's `AutoProcessor` used the torchvision backend, which differs from PIL
by at most 2 levels in about 0.5% of samples. That costs at most a relative 1.3e-5 in the pixel sum
(tolerance 1e-4) and stays within the cosine threshold. These figures come from the same one-off
script; the committed tests check only the tolerance (AC-SRCH-61) and the cosine (AC-SRCH-62).

Cosine similarity of each query to the best and second-best image, using the official embeddings:

| Query | Intended image | Next best |
|---|---|---|
| a black swan swimming on a lake | 0.777 | 0.555 |
| grapes growing on a vine in the garden | 0.782 | 0.574 |
| motor racing at a race track | 0.670 | 0.598 (rugby) |
| pub quiz question about sloths | 0.728 | 0.571 |
| rugby match in a stadium at night | 0.748 | 0.572 |
| managing images in a web service | 0.674 | 0.551 |

The q8 vision encoder meets its threshold, so the fp32 fallback isn't needed. The smallest ranking
margin is motor racing, at 0.072. The ranking test in phase 1 therefore checks the order, not the
scores.

## Consequences

- The `gemma2` jar grows by about 195 MB. Every build, test and runtime dependency of the image
  path is listed in [gemma2-vision-dependencies.md](../reference/gemma2-vision-dependencies.md).
- The image recipe joins the index recipe, so enabling images re-embeds every post once.
- Each image costs one vision-encoder pass, then about 270 tokens through the text model.
- The catalog's stored post format and the search index file format both move to version 2.
- Search quality for images depends on alt text only for snippets, not for matching.

## Delivery

Delivered in five phased PRs. The phases, their scope and their status are tracked in
[docs/plans/image-embeddings.md](../plans/image-embeddings.md).
