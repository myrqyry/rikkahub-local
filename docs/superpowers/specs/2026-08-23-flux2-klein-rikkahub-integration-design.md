# FLUX.2-klein Rikkahub Integration

## Goal

Integrate the validated upstream FLUX.2-klein LiteRT Kotlin runtime into Rikkahub as a dedicated local image-generation runtime. The first slice supports text-to-image only and assumes the model package is already staged in the app's external-files directory.

The pinned upstream reference is commit `f48a89e4f29a74ab51f29c311ac7a0e5e479d225` from `/tmp/opencode/litert-samples`. The upstream execution algorithm and graph order are preserved exactly. No graph optimization, PowerVR-specific rewrite, image editing, or package installer is included.

## Package Contract

The runtime consumes one installed package root:

```text
<external-files>/flux2-klein/
  graphs/
    ke_enc0.tflite
    ke_enc1.tflite
    ke_enc2.tflite
    kc_prep.tflite
    kc_double0.tflite
    kc_double1.tflite
    kc_single0.tflite
    kc_single1.tflite
    kc_single2.tflite
    kc_single3.tflite
    kc_final.tflite
    kv_vae.tflite
  klein_bins/
    inputs_embeds.bin
    enc_mask.bin
    enc_cos.bin
    enc_sin.bin
    cos.bin
    sin.bin
    temb.bin
    dsigma.bin
    bn_mean.bin
    bn_std.bin
    unpack_perm.bin
    unpatch_perm.bin
    latents0.bin
  klein_tokenizer/
    qwen_vocab.txt
    qwen_merges.txt
    qwen_embed_fp16.bin
```

The package layout is the future installer seam. Manual staging and a later resumable installer must produce the same package contract; the runtime does not know or care how the package arrived.

`Flux2KleinPackageValidator` checks required paths, regular-file status, and nonzero sizes. It reports three explicit states:

- `READY`: all graphs, host tensors, and tokenizer assets are present and valid; arbitrary prompt input is supported.
- `READY_BAKED_PROMPT`: graphs and host tensors are present but tokenizer assets are absent; only the staged reference prompt can run, for diagnostics.
- `NOT_READY`: required graphs or host tensors are missing or invalid.

Normal provider generation requires `READY`. `READY_BAKED_PROMPT` must never silently ignore the user's prompt or count as production acceptance.

## Runtime Boundary

The `local-llm` module owns the runtime and package model. It exposes a neutral boundary similar to:

```text
LiteRtImageGenerationRuntime
  status(): Flux2KleinPackageStatus
  generate(prompt, onProgress): Bitmap
  close()
```

The runtime owns:

- package validation and staged-input loading;
- tokenizer and fp16 embedding lookup when the package is `READY`;
- one shared LiteRT `Environment` per runtime instance;
- `ChunkRunner`, adapted from upstream;
- sequential `CompiledModel` execution with FP32 GPU precision;
- explicit input/output buffer and graph closure;
- the exact upstream prompt encoder, four-step scheduler, denoise graph order, latent permutations, and VAE decode;
- progress events after prompt encoding, each denoise step, and decode;
- conversion of the final output to a 256x256 ARGB bitmap.

The runtime does not own provider settings, model registry state, Compose UI, persistence, downloads, or image editing.

The upstream execution order remains:

```text
ke_enc0 -> ke_enc1 -> ke_enc2
repeat four steps:
  kc_prep
  kc_double0 -> kc_double1
  kc_single0 -> kc_single1 -> kc_single2 -> kc_single3
  kc_final
kv_vae
```

Exactly one graph is resident at a time. The shared LiteRT environment is reused across calls to avoid the upstream OpenCL context leak, and every native tensor buffer and graph is closed before the next graph is loaded.

## Rikkahub Integration

The existing image flow remains the integration path:

```text
ImgGenVM
  -> GenerationService
    -> ProviderImageToolBackend
      -> ProviderManager
        -> Flux2KleinProvider
          -> LiteRtImageGenerationRuntime
```

Add a dedicated `ProviderSetting.Flux2Klein` and built-in image model. Do not dispatch FLUX through `StableDiffusionProvider`.

`Flux2KleinProvider` implements image generation only:

- list the built-in FLUX model;
- reject `NOT_READY` before LiteRT allocation with a clear missing-package error;
- reject normal generation in `READY_BAKED_PROMPT` with a clear missing-tokenizer error;
- invoke the runtime on the existing native/background execution path;
- map the final bitmap to the existing PNG `ImageGenerationItem` payload;
- allow `GenerationService` to persist the normal gallery artifact and receipt;
- leave `editImage()` unsupported.

The model registry metadata identifies the model as:

```text
type: IMAGE
inputModalities: TEXT
outputModalities: IMAGE
format: litert
runtime: litert-image
executionBackend: litert
hardwareAccelerator: gpu
```

The existing Image Generation screen is unchanged. Existing prompt, progress, cancel, error, persistence, and result surfaces are reused. No synthetic partial image is emitted in this slice; the provider emits one final image. Runtime progress is bridged into the existing generation progress surface.

## Memory Policy

FLUX uses a dedicated chunk-aware admission policy. Required memory is estimated from:

```text
largest active graph resident size
+ peak host tensor/intermediate allowance
+ output bitmap and PNG allowance
+ explicit safety reserve
```

The total installed package size is never used as the resident-memory requirement. The policy runs before the first graph is compiled and returns a user-actionable refusal when the current device budget cannot accommodate the peak estimate. It does not change graph execution or attempt optimization.

## Error and Lifecycle Rules

- Package status is checked before allocating LiteRT resources.
- A missing or invalid package never triggers download or repair.
- User cancellation propagates through the coroutine and closes the active graph/buffers/environment safely.
- Runtime close releases the shared LiteRT environment.
- Runtime errors retain the underlying cause in logs while exposing concise user-facing messages.
- The installed app data must be preserved during device verification; use `adb install -r` only.

## Testing and Acceptance

Unit tests cover:

- package state classification for `READY`, `READY_BAKED_PROMPT`, and `NOT_READY`;
- required-file validation and missing-tokenizer behavior;
- chunk-aware memory admission using peak graph size rather than total package size;
- model metadata and provider routing;
- provider payload conversion and unsupported editing behavior.

Device acceptance uses the known-good package manually staged under the Rikkahub external-files directory on a Pixel 10 Pro:

1. Validator reports `READY`.
2. Select FLUX.2-klein in Rikkahub's existing Image Generation flow.
3. Enter a prompt different from the staged reference prompt.
4. Generate without changing the runtime or graph settings.
5. Receive and persist a valid 256x256 image through the normal result/gallery path.
6. Confirm logcat shows LiteRT GPU delegation and no fallback or runtime error.

The following are explicitly deferred: download/install management, resumable transfers, hashes and provenance, free-space checks, partial-install recovery, model-state UI, image editing, arbitrary installed package revisions, richer graph-level progress, cancellation between chunks, and compile/cache optimization.
