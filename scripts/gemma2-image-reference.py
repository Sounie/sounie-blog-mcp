#!/usr/bin/env python3
"""Reference data for the EmbeddingGemma 2 image spike (ADR 0011, PR 0).

The reference embeddings come from Google's own model, `google/embeddinggemma-2` at the pinned
revision, run in fp32 PyTorch with the official processor. That model returns per-token states
already projected to 768 dimensions; like SentenceTransformers, the script mean-pools them over the
attention mask and L2-normalises.

For each image the script also runs the q8 ONNX graphs the gemma2 provider bundles on the same
processor output, and records their cosine against the official embedding (`onnxQ8Cosine`). Text
queries, with the production query prompt, get the same treatment: their ONNX cosine checks that
the pooling here matches the pipeline the ONNX graph was exported from.

Run outside the sandbox (it downloads the official weights and processor from huggingface.co).
transformers 5.19 has `EmbeddingGemma2Model` and `EmbeddingGemma2Processor` (4.57 does not); the
image processor (shared with Gemma 4) needs torch and torchvision:

    uv venv --python 3.12 .venv-spike && . .venv-spike/bin/activate
    uv pip install "transformers>=5.19" torch torchvision onnxruntime pillow numpy
    python scripts/gemma2-image-reference.py --mirror .hf-mirror \
        --out core/src/test/resources/gemma2-image-reference.json img1.jpg img2.png ...
"""

import argparse
import json
import pathlib

import numpy as np
import onnxruntime as ort
import torch
from PIL import Image
from transformers import AutoModel, AutoProcessor

OFFICIAL_REPO = "google/embeddinggemma-2"
OFFICIAL_REVISION = "914f7f89142e33e77833254d9c9b90c3cef7303b"
ONNX_REPO = "onnx-community/embeddinggemma-2-ONNX"
ONNX_REVISION = "daa72c51243991dfcaf9f9137d2c573d8f7790c0"

# The production query prompt (Gemma2InputPreparation) and one query per test image.
QUERY_PROMPT = "task: search result | query: "
QUERIES = [
    "a black swan swimming on a lake",
    "grapes growing on a vine in the garden",
    "motor racing at a race track",
    "pub quiz question about sloths",
    "rugby match in a stadium at night",
    "managing images in a web service",
]


def onnx_dir(mirror: pathlib.Path) -> pathlib.Path:
    return mirror / ONNX_REPO / "resolve" / ONNX_REVISION / "onnx"


def session(path: pathlib.Path) -> ort.InferenceSession:
    return ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])


def describe(name, value):
    array = np.asarray(value)
    return f"{name}: shape={array.shape} dtype={array.dtype}"


def load_official_model():
    model, loading = AutoModel.from_pretrained(
        OFFICIAL_REPO, revision=OFFICIAL_REVISION, dtype=torch.float32, output_loading_info=True
    )
    # A text-only load silently skips the vision weights; the reference must include them.
    if model.vision_tower is None or loading["missing_keys"]:
        raise SystemExit(f"incomplete model: vision_tower={model.vision_tower is not None} {loading}")
    return model.eval()


@torch.no_grad()
def official_embedding(model, inputs) -> np.ndarray:
    hidden = model(**inputs).last_hidden_state
    mask = inputs["attention_mask"].unsqueeze(-1).to(hidden.dtype)
    pooled = (hidden * mask).sum(dim=1) / mask.sum(dim=1)
    return torch.nn.functional.normalize(pooled, dim=-1)[0].numpy()


def onnx_embedding(text, input_ids, attention_mask, image_features) -> np.ndarray:
    empty = np.zeros((0, 512), dtype=np.float32)
    feeds = {
        "input_ids": np.asarray(input_ids, dtype=np.int64),
        "attention_mask": np.asarray(attention_mask, dtype=np.int64),
        "image_features": image_features.astype(np.float32),
        "video_features": empty,
        "audio_features": empty,
    }
    return text.run(["sentence_embedding"], feeds)[0][0]


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))


def embed_image(processor, model, vision, text, image_path: pathlib.Path) -> dict:
    image = Image.open(image_path).convert("RGB")
    inputs = processor(text=processor.image_token, images=[image], return_tensors="pt")
    print(f"\n{image_path.name} ({image.width}x{image.height})")
    for key, value in inputs.items():
        print("  " + describe(key, value))

    embedding = official_embedding(model, inputs)

    # The processor names the patch positions `image_position_ids`; the graph calls them
    # `pixel_position_ids`.
    image_features = vision.run(
        ["image_features"],
        {
            "pixel_values": inputs["pixel_values"].numpy().astype(np.float32),
            "pixel_position_ids": inputs["image_position_ids"].numpy().astype(np.int64),
        },
    )[0]
    print("  " + describe("image_features", image_features))
    onnx = onnx_embedding(text, inputs["input_ids"], inputs["attention_mask"], image_features)
    print(f"  onnx q8 cosine vs official: {cosine(embedding, onnx):.5f}")

    positions = inputs["image_position_ids"][0].numpy()
    real = positions[(positions >= 0).all(axis=1)]
    return {
        "file": image_path.name,
        "width": image.width,
        "height": image.height,
        "inputIds": inputs["input_ids"][0].tolist(),
        "patches": int(inputs["pixel_values"].shape[1]),
        "realPatches": int(real.shape[0]),
        "patchGrid": [int(real[:, 0].max()) + 1, int(real[:, 1].max()) + 1] if len(real) else [0, 0],
        "imageTokens": int(image_features.shape[0]),
        "pixelValuesSum": float(inputs["pixel_values"].to(torch.float64).sum()),
        "onnxQ8Cosine": cosine(embedding, onnx),
        "embedding": [float(v) for v in embedding],
    }


def embed_query(processor, model, text, query: str) -> dict:
    inputs = processor.tokenizer(QUERY_PROMPT + query, return_tensors="pt")
    embedding = official_embedding(model, inputs)
    onnx = onnx_embedding(
        text, inputs["input_ids"], inputs["attention_mask"], np.zeros((0, 512), dtype=np.float32)
    )
    ids = inputs["input_ids"][0].tolist()
    print(f"  {query!r}: {len(ids)} tokens {ids[:2]}..{ids[-1:]}, onnx q8 cosine {cosine(embedding, onnx):.5f}")
    return {
        "query": query,
        "inputIds": ids,
        "onnxQ8Cosine": cosine(embedding, onnx),
        "embedding": [float(v) for v in embedding],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mirror", type=pathlib.Path, required=True)
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("images", type=pathlib.Path, nargs="+")
    args = parser.parse_args()

    processor = AutoProcessor.from_pretrained(OFFICIAL_REPO, revision=OFFICIAL_REVISION)
    print("image processor:", json.dumps(processor.image_processor.to_dict(), default=str, indent=2))
    model = load_official_model()
    directory = onnx_dir(args.mirror)
    vision = session(directory / "vision_encoder_quantized.onnx")
    text = session(directory / "model_quantized.onnx")

    images = [embed_image(processor, model, vision, text, path) for path in args.images]
    print("\nqueries")
    queries = [embed_query(processor, model, text, query) for query in QUERIES]

    print("\nquery → best image (official embeddings)")
    image_matrix = np.array([image["embedding"] for image in images])
    for query in queries:
        scores = image_matrix @ np.array(query["embedding"])
        best = np.argsort(-scores)[:2]
        ranked = ", ".join(f"{images[i]['file']} {scores[i]:.3f}" for i in best)
        print(f"  {query['query']!r}: {ranked}")

    args.out.write_text(
        json.dumps(
            {
                "repo": OFFICIAL_REPO,
                "revision": OFFICIAL_REVISION,
                "comparedWith": {"repo": ONNX_REPO, "revision": ONNX_REVISION, "files": "q8"},
                "queryPrompt": QUERY_PROMPT,
                "images": images,
                "queries": queries,
            },
            indent=2,
        )
        + "\n"
    )
    print(f"\nwrote {args.out}")


if __name__ == "__main__":
    main()
