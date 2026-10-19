# Bonsai Image 4B — isolated runtime integration (2026-10-19)

## Why / state

A link to the LiteRT conversion of Bonsai Image 4B prompted investigation of
on-device generation in RikkaHub. The published Android CPU/XNNPACK sample is
real and includes tokenizer, host math, graph load/run/close logic, and
on-device tests. RikkaHub's existing FLUX.2-klein image runtime uses a
different set of chunked GPU graphs. They are **not interchangeable**.

This checkpoint implements a **separate source-level runtime and explicit
provider dispatch**, without downloading model weights, changing installed
Android data, or advertising an unverified model in the UI.

## Implemented

- Ported upstream `BonsaiMath.kt`, `BonsaiPipeline.kt`, `QwenTokenizer.kt`,
  and `PromptRules.kt` into
  `local-llm/.../litert/image/bonsai/`. Confirmed each port matches the
  source on revision `317d577f5f962eb2d5a3980bca2ce047a8338212`,
  apart from the Kotlin package declaration. Retained upstream copyright
  and Apache-2.0 notices.
- Pinned `pipeline_meta.json` to that revision, as an Android library asset
  with a verified SHA-256.
- `BonsaiBundle` declares the exact 256px graph and tokenizer names, sizes,
  SHA-256s and immutable HF download URLs. Availability is a cheap stat
  check; `requireVerified()` verifies all five full files before loading
  native graphs. The independent installation directory is
  `local-models/bonsai-image-ternary-4b/` under app external files.
- `BonsaiImageRuntime` uses the upstream three-graph CPU/XNNPACK path,
  sequentially loading text encoder -> DiT -> VAE. It verifies the bundle,
  rejects empty/overlong prompts, checks a conservative available-memory
  budget, passes cancellation at stage boundaries, and converts the actual
  returned RGB bytes into a Bitmap. It does **not** return mock images or
  fall back to FLUX on failure.
- `LiteRtProvider.generateImage` now selects by requested model ID.
  `flux2-klein` keeps the existing generator, `bonsai-image-ternary-4b`
  uses Bonsai, and an unknown LiteRT image ID is an explicit error instead
  of silently generating with FLUX. Koin provides the separate runtime.
- The Bonsai image model has a stable image descriptor but is **not**
  added to built-in provider choices, installed-model registry or model
  catalog, because no verified package is installed.

## Verification

- `:local-llm:compileDebugKotlin`, `:app:compileDebugKotlin`,
  `:local-llm:testDebugUnitTest` passed.
- All 26 upstream Python tokenizer golden cases passed against tokenizer
  files downloaded **only to a temporary directory** from the pinned
  revision. The fixture-driven test intentionally skips when those files
  are absent; it never downloads during a Gradle build.
- Bundle validation, SHA checks, host-side noise/position/sigma/unpatchify,
  pinned metadata graph selection, and provider dispatch have JVM tests.
- Upstream source matches the pinned revision for all four ported files.
- No ~4.26 GB model graph downloads; no Android install, inference,
  screenshots or GPU/CPU latency claims. The current Pixel 10 Pro's
  ability to finish generation is **not verified**.
- `git diff --check` passed; only additive runtime/source/test/DI changes
  plus an intentional correction to LiteRT image model selection.

## Remaining to be usable on the phone

1. Implement an explicit **multi-file verified package import** in the
   unified Models page (prefer SAF folder import or an opt-in PC transfer).
   Guard free storage and partial transfers; do not overwrite FLUX files.
2. Make Bonsai selectable only **after** all pinned files have passed
   complete SHA-256 verification. Do not mark a download or metadata as
   proof of runnable inference.
3. Run a real Pixel 10 Pro image-generation acceptance test with the
   installed package, record timing, memory (and background app eviction),
   actual decoded pixels, and cancellation/restart behavior.
4. Only after that, consider exposing resolution/seed/steps, model
   management/removal, and user-visible progress/receipt fields.

Upstream: https://huggingface.co/litert-community/Bonsai-Image-ternary-4B

The prior public branch contains private unpublished local history:
checkpoint locally; **do not push HEAD to a public remote**.
