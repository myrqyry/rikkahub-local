# Image Generation Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the local Stable Diffusion contract violations (count/aspect ratio), add explicit per-runtime image capabilities, remove base64 from the core pipeline, replace the RAM-admission formula with a real runtime-memory budget, serialize low-memory native release, and add generation receipts plus a real-device acceptance test.

**Architecture:** Keep the existing Provider → StableDiffusionBridge(JNI) → stable-diffusion.cpp stack. Add an `ImageCapabilities` contract that UI/agent-tool/validation read from instead of trusting providers to honor fields. Introduce `GeneratedImagePayload` (Bytes/File/Base64) to end the base64 round-trip for local generation. Serialize memory-pressure eviction onto the existing native generation lane.

**Tech Stack:** Kotlin, Coroutines, Room, Jetpack Compose (UI), JUnit (JVM tests), Android instrumented tests (device acceptance), JNI via existing `StableDiffusionBridge`.

## Global Constraints

- Keep `excp.rikkahub.local` applicationId (debug = `excp.rikkahub.local.debug`).
- Preserve installed app data: never uninstall, never `pm clear`, always `adb install -r` (repo rule).
- Do NOT run `connectedDebugAndroidTest` against a phone with real data (AGP uninstalls the target after the run). Use a disposable install or emulator.
- No mocks/placeholders to satisfy acceptance; every deliverable must be real.
- Local editing (`editImage`) stays unsupported until real img2img lands; the model registry must not advertise `IMAGE_EDITING` for the local runtime.
- JVM unit tests: `./gradlew :app:testDebugUnitTest`; lint: `./gradlew :app:lintDebug` (baseline in place).
- Prefer serial generation (reuse warm session) over `batch_count > 1` (memory bound).

---

### Task 1: Fix local count and aspect ratio (serial generation + profile-aware dims)

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt` (generateImage + helpers)
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProviderTest.kt`
- Consult: `ai/src/main/java/me/rerere/ai/provider/Provider.kt:67` (ImageGenerationParams.numOfImages, aspectRatio), `ai/src/main/java/me/rerere/ai/ui/Image.kt` (ImageAspectRatio)

**Interfaces:**
- Consumes: `ImageGenerationParams {model, prompt, numOfImages=1, aspectRatio=SQUARE, partialImages=2}`; `ImageAspectRatio {SQUARE, LANDSCAPE, PORTRAIT}`; `SdGenerationProfile` from `SdCatalog.findByModelFile(modelId)?.generationProfile`; `StableDiffusionBridge.Backend {CPU, VULKAN}`; `GenerationPhase`.
- Produces: `resolveAspectDimensions(aspectRatio, profile): Pair<Int,Int>` — resolves width/height from the model's generation profile (not a hardcoded universal pair). Emits one `ImageGenerationItem` per image, looping `params.numOfImages` times serially, reusing the warm session.

- [ ] **Step 1: Write failing JVM tests**

Add to `StableDiffusionProviderTest.kt`:
- `aspectRatio selects profile-aware dimensions`: calling the aspect-resolution helper with `LANDSCAPE` + a 512-oriented profile yields a landscape pair (e.g. 768×512) and `PORTRAIT` yields 512×768; `SQUARE` keeps profile dims.
- `numOfImages emits that many items`: a test-double generation flow with `numOfImages=3` emits 3 `ImageGenerationItem`s (verify via a flow collector).

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*StableDiffusionProviderTest"`.
Expected: FAIL (helper/behavior does not exist yet).

- [ ] **Step 3: Implement `resolveAspectDimensions` and serial loop**

In `StableDiffusionProvider.kt` add:

```kotlin
internal fun resolveAspectDimensions(
    aspectRatio: ImageAspectRatio,
    profile: SdGenerationProfile?,
): Pair<Int, Int> {
    val (w, h) = when {
        profile != null -> profile.width to profile.height
        else -> 512 to 512
    }
    return when (aspectRatio) {
        ImageAspectRatio.SQUARE -> w to h
        ImageAspectRatio.LANDSCAPE -> maxOf(w, h) to minOf(w, h)
        ImageAspectRatio.PORTRAIT -> minOf(w, h) to maxOf(w, h)
    }
}
```

Change the generation body: compute `(width, height)` from `resolveAspectDimensions(params.aspectRatio, profile)` instead of `resolveEffectiveGenerationParams` dims; wrap the emit path in `repeat(params.numOfImages) { ... generateNativeWithCancellation ... emit(item) ... }`, keeping the warm session across iterations (do not `invalidateSession` between images). Keep the 120s sampling timeout per image.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*StableDiffusionProviderTest"`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt app/src/test/java/me/rerere/rikkahub/data/ai/StableDiffusionProviderTest.kt
git commit -m "fix: honor local image count and aspect ratio"
```

### Task 2: Add ImageCapabilities contract and drive validation from it

**Files:**
- Create: `ai/src/main/java/me/rerere/ai/provider/ImageCapabilities.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/ModelDescriptor.kt` (attach capabilities), `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt`, `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt`
- Test: new JVM test `app/src/test/java/me/rerere/rikkahub/data/ai/ImageCapabilitiesTest.kt`

**Interfaces:**
- Consumes: `ImageAspectRatio`; local runtime identity (`LocalRuntime.StableDiffusion`); `ModelCapability` enum used by `ModelRegistry.setCapabilityEnabled`.
- Produces: `data class ImageCapabilities(generation: Boolean, editing: Boolean, maxOutputs: Int, supportedAspectRatios: Set<ImageAspectRatio>, supportsSeed: Boolean, supportsNegativePrompt: Boolean, supportsSteps: Boolean, supportsCfg: Boolean, supportsPartialPreview: Boolean, maxReferenceImages: Int)`; `val ProviderSetting.imageCapabilities: ImageCapabilities` (or a resolver function on the registry).

- [ ] **Step 1: Write failing JVM test**

`ImageCapabilitiesTest.kt`:
- `local provider exposes bounded capabilities`: a StableDiffusion setting reports `editing=false`, `maxOutputs` clamped to a sane local cap, `supportsPartialPreview=false`.
- `capability filter hides unsupported aspect ratios`: a capabilities set without `LANDSCAPE` filters it out of a requested list.

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*ImageCapabilitiesTest"`.
Expected: FAIL.

- [ ] **Step 3: Implement `ImageCapabilities`**

Create `ImageCapabilities.kt` in `ai` module with the exact data class from the interface block. Add a resolver (e.g. in `ModelRegistry`/`ModelDescriptor` or an extension in the app module) returning per-runtime capabilities; local SD returns `editing=false`, `generation=true`, `supportedAspectRatios` = all three, `maxOutputs` = a local bound (e.g. 4), `supportsPartialPreview=false`. Wire `ImgGenVM` to consult it when enabling/disabling UI controls (image count cap, aspect ratio options, partial-preview toggle) and to clamp `numOfImages`.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*ImageCapabilitiesTest"`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai/src/main/java/me/rerere/ai/provider/ImageCapabilities.kt app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt app/src/main/java/me/rerere/rikkahub/data/modelregistry/ app/src/test/java/me/rerere/rikkahub/data/ai/ImageCapabilitiesTest.kt
git commit -m "feat: add image capability negotiation"
```

### Task 3: Replace base64 as the internal representation

**Files:**
- Create: `ai/src/main/java/me/rerere/ai/ui/GeneratedImagePayload.kt`
- Modify: `ai/src/main/java/me/rerere/ai/ui/Image.kt` (`ImageGenerationItem`), `app/src/main/java/me/rerere/rikkahub/data/media/ImageMediaStore.kt`, `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt`, cloud provider adapters that emit `ImageGenerationItem`, `ImgGenVM` (if it reads `item.data`)
- Test: `app/src/test/java/me/rerere/rikkahub/data/media/ImageMediaStoreTest.kt` (existing), plus new payload test

**Interfaces:**
- Consumes: `ImageGenerationItem` (current base64-string shape); `FilesManager` (`getImagesDir`, `createImageFileFromBase64`); `MediaArtifactRef`.
- Produces: `sealed interface GeneratedImagePayload { data class Bytes(bytes: ByteArray, mimeType: String); data class File(path: String, mimeType: String); data class Base64(data: String, mimeType: String) }`; `ImageGenerationItem` evolves to carry `payload: GeneratedImagePayload` (keep `partial`/`partialImageIndex`). `ImageMediaStore.saveGenerated` accepts the payload and writes a file directly for `Bytes`/`File`, only base64-decoding for `Base64`.

- [ ] **Step 1: Write failing JVM tests**

- Payload test: `File` payload round-trips through the media store without a base64 step (assert the written file bytes equal the source bytes).
- `Base64` payload still decodes (cloud path unchanged).

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*ImageMediaStore*"`.
Expected: FAIL (new type does not compile/behave).

- [ ] **Step 3: Implement the payload sealed interface and evolve items**

Create `GeneratedImagePayload.kt`. Evolve `ImageGenerationItem` to hold `payload` (update `@Serializable` shape). In `StableDiffusionProvider`, emit `ImageGenerationItem(payload = GeneratedImagePayload.Bytes(pngBytes, "image/png"), ...)` — no base64 encode. Cloud adapters keep producing `Base64` payloads (decode at their boundary is unchanged behavior from their perspective). Update `DefaultImageMediaStore.saveGenerated` to write bytes/file directly and only base64-decode for the `Base64` case. Update any reader of `item.data` (e.g. `ImgGenVM`, image tools) to use `payload`.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest` and `./gradlew :ai:testDebugUnitTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai/src/main/java/me/rerere/ai/ui/GeneratedImagePayload.kt ai/src/main/java/me/rerere/ai/ui/Image.kt app/src/main/java/me/rerere/rikkahub/data/media/ImageMediaStore.kt app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt app/src/test/java/me/rerere/rikkahub/data/media/
git commit -m "refactor: replace base64 in core image pipeline"
```

### Task 4: Real runtime-memory budget for admission

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt` (`sdMemoryPolicyViolation`, `deviceTotalRamBytes`)
- Create: `app/src/main/java/me/rerere/rikkahub/data/ai/RuntimeMemoryProfile.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/ai/StableDiffusionProviderTest.kt` (existing memory-policy tests) + new `RuntimeMemoryProfileTest.kt`

**Interfaces:**
- Consumes: `ActivityManager.MemoryInfo` (`availMem`, `totalMem`, `lowMemory`); `Runtime.getRuntime().maxMemory()`/`totalMemory()`.
- Produces: `data class RuntimeMemoryProfile(modelResidentEstimate: Long, workspaceEstimate: Long, outputEstimate: Long, safetyMargin: Long)`; `fun estimateRuntimeBudget(availMem: Long, androidReserve: Long, knownWorkingSet: Long): Long`; admission compares the sum of estimates + margin against `availMem - androidReserve - knownWorkingSet`, with an intentionally conservative default.

- [ ] **Step 1: Write failing JVM test**

`RuntimeMemoryProfileTest.kt`:
- `budget is net of reserves`: a device with `availMem=6GB`, `androidReserve=1.5GB`, `knownWorkingSet=0.5GB` yields a `4GB` budget; a model needing `>4GB` is refused.
- `workspace estimate scales with resolution`: doubling output dimensions more than doubles the estimate (conservative).

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*RuntimeMemoryProfile*"`.
Expected: FAIL.

- [ ] **Step 3: Implement the budget**

Create `RuntimeMemoryProfile.kt`. Replace `sdMemoryPolicyViolation` to compute `modelResidentEstimate` (file size) + `workspaceEstimate` (conservative function of width×height×steps×model scale, defaulting high) + `outputEstimate` (width×height×4×batch) + `safetyMargin` (fixed fraction), and compare against `deviceTotalRamBytes()` budget reduced by a required Android reserve and the known Rikka working set (`Runtime.getRuntime().maxMemory()` as a lower-bound proxy). Keep behavior deterministic for JVM tests (inject the RAM value as a parameter, as the current tests already do).

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*RuntimeMemoryProfile*"` and `--tests "*StableDiffusionProviderTest"`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/RuntimeMemoryProfile.kt app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt app/src/test/java/me/rerere/rikkahub/data/ai/RuntimeMemoryProfileTest.kt
git commit -m "fix: use real runtime memory budget for image admission"
```

### Task 5: Serialize low-memory native release onto the runtime lane

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionBridge.kt`, `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt`
- Test: `app/src/androidTest/java/me/rerere/rikkahub/data/ai/StableDiffusionBridgeTest.kt` (device test)

**Interfaces:**
- Consumes: `onTrimMemory`/`onLowMemory` callbacks; `nativeCancel()`; `nativeRelease()`; the generation mutex already serializing native calls.
- Produces: `@Volatile var evictionRequested: Boolean` on the bridge; `fun requestEviction()` (sets flag; calls `nativeCancel()` if a generation is in flight) replacing direct `invalidateSession()` from the callback; the generation lane checks the flag after sampling unwinds and calls `invalidateSession()` (release) on the native dispatcher — never inside the Android lifecycle callback.

- [ ] **Step 1: Write the device test (fails first)**

`StableDiffusionBridgeTest.kt`: simulate eviction while a session is warm and assert the release is deferred and executed on the native lane, not synchronously from the callback thread. (No real model required — assert the state transition and dispatch path.)

- [ ] **Step 2: Run to confirm failure**

Run on a disposable install (NOT the primary phone): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.data.ai.StableDiffusionBridgeTest`.
Expected: FAIL.

- [ ] **Step 3: Implement the eviction lane**

Add `requestEviction()` to the bridge; update the provider's `ComponentCallbacks2` to call `bridge.requestEviction()` instead of `bridge.invalidateSession()`. In the generation flow, after `generateNativeWithCancellation` completes (success, timeout, or cancellation), check `evictionRequested` and, if set, `withContext(nativeDispatcher) { bridge.invalidateSession() }` and clear the flag. Never touch the native session from the callback thread.

- [ ] **Step 4: Run the test again**

Run the device test on a disposable install.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionBridge.kt app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt app/src/androidTest/java/me/rerere/rikkahub/data/ai/StableDiffusionBridgeTest.kt
git commit -m "fix: serialize low-memory native release"
```

### Task 6: Generation receipt / provenance

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationReceipt.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt` (populate receipt), `app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageToolResult.kt` (optional extension), `app/src/main/java/me/rerere/rikkahub/data/media/ImageMediaStore.kt` (`MediaArtifactRef`)
- Test: `app/src/test/java/me/rerere/rikkahub/data/ai/GenerationReceiptTest.kt`

**Interfaces:**
- Consumes: `StoredImageArtifact`; `MediaArtifactRef`; model id + revision from `ModelDescriptor`; runtime/backend (CPU/VULKAN/cloud); actual emitted dims, seed, steps, cfg, sampler/scheduler, elapsed.
- Produces: `@Serializable data class GenerationReceipt(artifactId, modelId, modelRevision, runtime, backend, width, height, seed, steps, cfg, sampler, scheduler, elapsedMs, sourceArtifacts: List<MediaArtifactRef>)`; emitted alongside artifacts in the image-tool result envelope.

- [ ] **Step 1: Write failing JVM test**

`GenerationReceiptTest.kt`: build a receipt from a test-double provider run and assert every field round-trips through kotlinx.serialization, including empty source lists and null revision.

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*GenerationReceipt*"`.
Expected: FAIL (type does not exist).

- [ ] **Step 3: Implement the receipt**

Create `GenerationReceipt.kt` with the exact shape above. Populate it in `StableDiffusionProvider.generateImage` (and the cloud path) using the resolved effective params (width/height/steps/cfg), the actual seed used, the elapsed sampling time, and the source artifact refs. Attach to the `ImageToolResult` output when available.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*GenerationReceipt*"`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/GenerationReceipt.kt app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionProvider.kt app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageToolResult.kt app/src/test/java/me/rerere/rikkahub/data/ai/GenerationReceiptTest.kt
git commit -m "feat: add generation receipts"
```

### Task 7: Real-device acceptance test

**Files:**
- Create: `app/src/androidTest/java/me/rerere/rikkahub/data/ai/StableDiffusionDeviceAcceptanceTest.kt`
- Modify: none (test-only), but read `app/src/main/java/me/rerere/rikkahub/data/ai/StableDiffusionBridge.kt`

**Interfaces:**
- Consumes: a small known-good SD model file on a disposable install (never the primary phone); `StableDiffusionBridge` directly (native). Use `adb install -r` + run via instrumented test on an emulator/disposable device; **never** run against the primary phone (data-loss rule).

- [ ] **Step 1: Write the device test**

Flow: ensureLoaded → ensureSession(smallModel, CPU) → nativeGenerate(width, height, prompt) → assert non-null, non-empty RGBA of expected dimensions → nativeCancel → re-generate on the same warm session → assert second generation succeeds (warm reuse, no reload). Skip gracefully (assumeTrue) when the model file is absent so CI without the fixture doesn't hard-fail.

- [ ] **Step 2: Run on a disposable device**

`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.data.ai.StableDiffusionDeviceAcceptanceTest`.
Expected: PASS on a device with the small model present.

- [ ] **Step 3: Document the manual procedure in `docs/references/`**

Add a short note (path: `docs/references/image-gen-device-acceptance.md`) listing the small model to use, install commands (`adb install -r`), the emulator/disposable-device requirement, and expected assertions.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/me/rerere/rikkahub/data/ai/StableDiffusionDeviceAcceptanceTest.kt docs/references/image-gen-device-acceptance.md
git commit -m "test: add real-device image generation acceptance"
```

---

## Verification Gate

- `./gradlew :app:testDebugUnitTest :ai:testDebugUnitTest :app:lintDebug --no-daemon` — green (lint via existing baseline).
- `./gradlew assembleDebug` — green; install with `adb install -r` on the primary phone only when no real data risk (it is a fresh install).
- Device acceptance test runs only on a disposable install/emulator per the data-preservation rule.
