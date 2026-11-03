# EmbeddingGemma 2 512d vs LFM3-VL-2B — integration investigation

**Observed 2026-10-28.** Source: current RikkaHub local tree, Hugging Face
repository API/cards, Liquid AI license, llama.cpp merged PR and release.
This is investigation, **not implementation or runtime verification**.
No model files were downloaded, and no app source or saved data was changed.

## Two models with different purposes

| Candidate | What it does | Relevance | Integration call |
| --- | --- | --- | --- |
| `tomaarsen/embeddinggemma-2-512d` | Multimodal *embedding* vectors at 512d, using an SVD-adjusted projection. Not a question-answering VLM. | RAG storage/retrieval optimization | **Evaluate first using current 768d LiteRT models and native Matryoshka postprocessing**, no replacement weights |
| `LiquidAI/LFM3-VL-2B` | 1.92B-parameter visual *generative language* model: image/video question answering, diagrams, UI perception, spatial reasoning. Not an embedding model. | RikkaHub visual chat and Veyax/Stagehand perception | **Qualify a new llama.cpp runtime and projector path before listing as supported on Android** |

## 512-dimensional SVD derivative

HF main revision `f7e16920485c2666d65ec566debc023e5e9d0319`
(2026-10-28, not gated, Apache-2.0); `model.safetensors` =
1,488,653,144 bytes. The whole 744.2M-param model remains; only its
final 512→768 projection has become 512→512, so it is **not** a smaller
encoder or an Android LiteRT replacement.

Author's claim: factor original final projection as `W = U Σ Vh`;
replace with `Σ Vh`. Because U has orthonormal columns, pairwise
inner products and cosine similarities are preserved absent BF16
roundoff. The output coordinates differ, so vectors are **not** byte- or
coordinate-compatible with our existing 768d vectors or original
512d-prefix truncation. No cross-index mixing.

Author-reported 13 English NanoBEIR mean NDCG@10:
- full original 768d: 63.362
- standard original Matryoshka 512d prefix: 62.746
- SVD derivative 512d: 63.385

Unverified outside that author's tests; no phone benchmark.
Critically, the derivative **does not preserve native Matryoshka
truncation** below 512d (author's own 256d /128d comparisons degrade).

**What RikkaHub already has:** `EmbeddingGemma2Backend.kt` returns
768 finite float values and `EmbeddingGemma2Variant.kt` hardcodes 768
per variant; vector store tracks dimension and a per-model
`embeddingSpaceId`. Google's original model is trained for prefix
dimensions **768/512/256/128**, followed by L2 re-normalization. A
safe, independently measurable approach is postprocessing the *existing*
LiteRT output for 512d/256d with a distinct space ID per
(model revision, dimension, normalization/version), and reindexing.
This saves 33%/67% of raw float32 vector storage, without new weights.
The SVD derivative could be worth porting only if a measured 512d
ranking gap justifies an actual model conversion and its integration
cost. It is not a simple toggle for the current `.litertlm` bundle.

**Proof criteria** for a future feature: RED-first tests for 768→512/256
prefix, re-normalization, zero/NaN rejection, query-document matching
dimensions, isolated vector IDs/spaces and non-destructive migration;
then on-device sample retrieval quality/timing for all three variants.

## LFM3-VL-2B

Official model revision `ba44bd51db20a2753f28c273a1571ccaa210d954`
at 2026-10-28; BF16 `model.safetensors` = 3,836,417,920 bytes.
1.92B total parameters (1.73B language, 164M vision encoder, 25M
projector). 128K **claimed model context**, not a feasible proven
Android working context. Supports images, text and sampled video in
its full inference implementations. Its card specifically emphasizes
document/chart/screen, grounding and spatial questions, and cautions
against using it for coding, long multi-turn chat or complex reasoning.
Liquid benchmark figures are vendor-reported, not measured on this phone.

Official GGUF revision `b36554cfd35854b06429900131730cd80f092cdb`:
- Q4_K_M main: 1,099,627,808 bytes; QAD-Q4_0 main: 1,049,615,648 B
- F16 vision projector: 378,996,032 B; Q8 projector: 203,031,872 B
- Q4_K_M+F16 = 1,478,623,840 B *on disk*, before context/KV,
  prompt images, native allocators or prefill cache. Precise phone peak
  RAM/latency is **not established**.
- Official ONNX q4 decoder+q8 vision weights: ~1.69 GB combined,
  but require additional integration, model processor and runtime.

**Latest compatibility discovery:** upstream llama.cpp merged LFM3-VL
support in PR #31252 on Oct 27; its **v0.8.0** release on Oct 28
includes LFM3 text, vision, audio and omni support. Current HF model
card recommends llama.cpp `b11960` or newer, which may differ from
earlier card text/PR notes about video; all runtime modalities must be
validated separately.

**What RikkaHub actually ships:** `com.llamatik:library:1.10.1`
(pinned from Oct 7), including `MultimodalBridge`, and a
GGUF+mmproj local vision provider. This predates LFM3 support upstream;
being able to classify GGUF is **not** evidence that the bundled
native library understands `lfm3`/the new vision encoder. The
`MmprojLocator.kt` currently recognizes only
`<model-filename-stem>.mmproj.gguf` or `mmproj.gguf`.
Official Liquid artifacts use `mmproj-LFM3-VL-2B-F16.gguf` etc, so
they are **not automatically found**. RikkaHub also forwards only
the first attached image in its local vision stream, and has no
LFM3-specific video preprocessing route.

**License:** LFM Open License v1.0, **not** Apache-2.0. Allows free
commercial use for organizations with less than $10M annual revenue,
plus research/nonprofit terms, and requires license/attribution
retention; review full text before redistribution or bundling.
Link: https://www.liquid.ai/lfm-license

**Practical rollout path:**
1. On desktop only, run *current* llama.cpp v0.8.0+ with official Q4
   GGUF + matching official projector; prove one image question and
   collect native errors, speed and memory.
2. Use a local OpenAI-compatible endpoint from the Android app as a
   separate **network** capability (requires explicit permission and
   reachable endpoint; never mislabel it on-device). This would allow
   early UI experiments without replacing Llamatik.
3. Update/replace the bundled Android llama.cpp integration only once
   LFM3 architecture, mmproj, image preprocessing and native ABI are
   confirmed compatible. Test image, tools, thinking, and cancel/
   memory under Android; test video separately (native support differs).
4. Add discovery and verified *paired* model/projector download with
   exact revision and hashes; ensure importing a projector cannot
   overwrite or masquerade as a language-model GGUF.
5. Only then advertise LFM3-VL-2B as a supported **local** model.

**Resource boundary:** yuzu-mint currently has ~5.0 GiB filesystem
available and reports 100% usage, with no LFM3 weights cached.
No large downloads are justified just to evaluate metadata.

## Sources / verification

- https://huggingface.co/tomaarsen/embeddinggemma-2-512d
- https://huggingface.co/google/embeddinggemma-2
- https://huggingface.co/docs/transformers/model_doc/embedding_gemma2
- https://huggingface.co/LiquidAI/LFM3-VL-2B
- https://huggingface.co/LiquidAI/LFM3-VL-2B-GGUF
- https://huggingface.co/LiquidAI/LFM3-VL-2B-ONNX
- https://github.com/ggml-org/llama.cpp/pull/31252
- https://github.com/ggml-org/llama.cpp/releases
- https://www.liquid.ai/lfm-license
- Local repo: `EmbeddingGemma2Backend.kt`, `EmbeddingGemma2Variant.kt`,
  `EmbeddingRepository.kt`, `LlamaCppProvider.kt`, `MmprojLocator.kt`,
  `local-llm/build.gradle.kts` at commit `3043b664f`.

No code or checkpoint compatibility was inferred from benchmark claims;
these are candidate paths awaiting physical-device validation.
