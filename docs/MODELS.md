# Models

Models are **never bundled** in the APK and are downloaded only when you ask. Files live in app-private storage
(`files/models/`) and are verified before use.

| | Qwen3 4B Instruct (2507) — default | Qwen2.5 1.5B Instruct — light |
|---|---|---|
| Publisher | Qwen Team (Alibaba Cloud); GGUF by Unsloth | Qwen Team (Alibaba Cloud) |
| Parameters / quantization | 4.0 B / Q4_0 | 1.5 B / Q4_0 |
| File size | 2,375,773,280 bytes (2.38 GB) | 1,066,227,232 bytes (1.07 GB) |
| SHA-256 | `e0ba675d86ab277c61701c6793659b2ae801d95e3be791464c321e6fbf613be2` | `dcd819ff094852c38faba6873d8ff0c9d51eadb2844539e52042ae5d647bbfdb` |
| License | Apache-2.0 | Apache-2.0 |
| Source | https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF | https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF |
| Context used | 4096 tokens | 4096 tokens |
| Suggested RAM | 8 GB or more | 4 GB or more |

Sizes and hashes were read from the publishers' Hugging Face file metadata, and each model was downloaded and
hashed independently. The downloader refuses any file whose size or SHA-256 differs.

Why these: both are instruction-tuned, Apache-2.0, ungated, and small enough for a phone. The default is a
non-thinking instruct variant, so it does not emit `<think>` blocks that would break structured output. Q4_0 is
chosen because llama.cpp repacks it for the ARM i8mm/dot-product kernels.

## Adding a model

Add a `ModelDescriptor` to `ModelCatalog` with the exact size and SHA-256 from the publisher. `ModelCatalogTest`
enforces https URLs, a 64-hex hash and unique ids. The chat template is read from the GGUF; templates that
llama.cpp's built-in formatter does not recognise fall back to ChatML, so verify a new family with
`LlamaAiEngineTest` before listing it.

## Licensing notes

* llama.cpp is MIT. The Backdrop library is Apache-2.0. Both are compatible with this project's Apache-2.0 license.
* Model weights are not redistributed by this project; you download them from their publishers under their license.
