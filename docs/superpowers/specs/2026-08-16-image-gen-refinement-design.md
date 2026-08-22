# Image Generation Refinement — Design

> **Status:** Design for the post-P0 image-generation refinement plan.
> **Context:** Follows the AI Studio review (kept verbatim in session m0251). The local `stable-diffusion.cpp` runtime is real and well-built; the work is boundary/capability refinement plus a handful of correctness fixes — NOT a rewrite.

## Decision

Keep `stable-diffusion.cpp` (GGUF/safetensors, CPU+Vulkan) as RikkaHub's general local diffusion runtime. Do not migrate to LiteRT. LiteRT remains a future *additional* runtime for models exported/optimized for it.

## Current pipeline (verified against source)

```
RikkaHub UI / Agent tool
  → ImageGenerationParams {model, prompt, numOfImages, aspectRatio, partialImages}
  → StableDiffusionProvider.generateImage()
      → StableDiffusionBridge (JNI) → bridge.cpp → stable-diffusion.cpp → sd_image_t → RGBA
      → Bitmap → PNG ByteArray → Base64 String → ImageGenerationItem(data=String)
  → DefaultImageMediaStore.saveGenerated() → createImageFileFromBase64() → GenMediaEntity
```

Key sources:
- `ai/src/main/java/me/rerere/ai/ui/Image.kt` — `ImageGenerationItem(data: String, mimeType, partial, partialImageIndex)`; `ImageAspectRatio {SQUARE, LANDSCAPE, PORTRAIT}`
- `ai/src/main/java/me/rerere/ai/provider/Provider.kt:67` — `ImageGenerationParams`
- `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt` (429 lines) — provider, `deviceTotalRamBytes()`, `onTrimMemory`/`onLowMemory` → `bridge.invalidateSession()`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt` (399 lines) — passes numOfImages/aspectRatio into params
- `app/src/main/java/me/rerere/rikkahub/data/media/ImageMediaStore.kt` — `DefaultImageMediaStore.saveGenerated()`; `MediaArtifactRef`; `filesManager.createImageFileFromBase64()`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageToolResult.kt` — `StoredImageArtifact(artifactId, path, uri, galleryId, mimeType, width, height)`; `ImageToolResult` envelope; `ImageOperation`
- `app/src/main/java/me/rerere/rikkahub/data/modelregistry/` — `ModelRole`/`ModelRegistry`/`ModelRoleResolver`/`ModelSourcePolicy` (`IMAGE_EDITING` role exists)
- Native: `bridge.cpp` hard-codes `gen.batch_count = 1`

## Confirmed defects

1. **Local `numOfImages` ignored** — `StableDiffusionProvider` derives dims from provider setting / model profile, performs one native generation; `batch_count = 1`.
2. **Local `aspectRatio` ignored** — same path.
3. **Local editing unsupported** — `editImage()` explicitly refuses (correct behavior; registry must not advertise IMAGE_EDITING for local runtime until img2img lands).
4. **Base64 round-trip** in core pipeline (native→RGBA→Bitmap→PNG→base64→decode→file).
5. **Memory estimate `modelFileSize + width*height*4` vs total RAM** — not a real runtime budget; can over-admit on 8 GB phones.
6. **`onTrimMemory`/`onLowMemory` → `invalidateSession()`** may block the lifecycle callback on the generation mutex while a generation unwinds.

## Ordered change list (implementation order)

1. Fix local `count` and `aspectRatio` — serial generation reusing warm session; model-profile-aware aspect dimensions beside `resolveEffectiveGenerationParams()`.
2. Add explicit `ImageCapabilities` per model/runtime; drive UI controls, agent tool schema, and request validation from capabilities.
3. Replace base64 as universal internal representation — `GeneratedImagePayload` sealed interface (Bytes/File/Base64); cloud adapters decode at boundary; local sd.cpp → PNG/file without base64.
4. Replace RAM formula with runtime-memory budget (`ActivityManager.available` − Android reserve − known working set); `RuntimeMemoryProfile`; conservative estimate.
5. Move low-memory native release onto the serialized runtime lane (mark eviction → nativeCancel if needed → release after generation unwinds).
6. Add richer generation receipt (model ID+revision, runtime, backend CPU/Vulkan/cloud, actual W×H, seed, steps, CFG, sampler, scheduler, elapsed, source artifacts).
7. Real-device acceptance test: install small model → generate → validate dimensions/nonempty → cancel → regenerate → verify warm reuse.

## Architecture target

```
UI / Agent Tool
   │
   ▼
ImageGenerationService (resolve model+role, validate capabilities, privacy)
   │
   ├─ Cloud Image Runtime (OpenAI/Google)
   └─ Local Image Runtime (sd.cpp / GGML; LiteRT later)
   │
   ▼
ImageArtifactSink
   │
   ▼
StoredImageArtifact + receipt
```

## Preserved designs (do not regress)

- `StableDiffusionBridge` JNI, warm `(modelPath, backend)` session, native cancel/progress, CPU/Vulkan selection.
- `StoredImageArtifact` shape (artifactId, path, uri, galleryId, mimeType, width, height).
- Agent tools `generate_image` / `edit_image` / `analyze_image` / `extract_text_from_image` + `ImageToolResult` envelope.
- Existing tests around SD, image tools, artifact results, storage, role resolution, UI integration.

## Not in scope

- LiteRT runtime integration (separate future runtime).
- img2img local editing implementation (advertise only when supported).
- Model-role routing redesign.
