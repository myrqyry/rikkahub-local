# GenerationService Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single app-side `GenerationService` facade that owns model-resolution → privacy-gate → provider-invoke → persist → receipt for image generation, image editing, and OCR, then rewire the three duplicate consumers onto it.

**Architecture:** Thin app-module facade (`GenerationService`) composing existing seams: `ModelRoleResolver` (role→model), `ImageToolBackend` (provider invoke), `ImageMediaStore` (persistence). Resolution + privacy gate + receipt building move INTO the service (user decision m1868). Consumers `ImgGenVM`, `ImageTools`, `ImageTextExtractor` delegate; `ImageTools.buildGenerationReceipt` is deleted and re-homed in the service. Koin binding next to the existing `ImageToolBackend` binding.

**Tech Stack:** Kotlin, kotlinx.coroutines (suspend flows), Koin DI (app module), JUnit (JVM tests), kotlinx.serialization (GenerationReceipt is already `@Serializable`).

## Global Constraints

- Service lives at `app/src/main/java/me/rerere/rikkahub/data/ai/generation/GenerationService.kt`.
- `GenerationOutcome(artifacts: List<StoredImageArtifact>, receipt: GenerationReceipt, prompt: String, modelName: String)`.
- Receipt building logic comes from `ImageTools.buildGenerationReceipt` (artifact, providerSetting, modelId, elapsedMs, sourceArtifacts) — moved verbatim into the service.
- Service signature: `GenerationService(modelRoleResolver: ModelRoleResolver, backend: ImageToolBackend, mediaStore: ImageMediaStore)`.
- Public methods: `suspend generate(settings, assistant, params, sourceArtifacts=emptyList()): GenerationOutcome`; `suspend edit(settings, assistant, params, sourceArtifacts): GenerationOutcome`; `suspend analyze(settings, assistant, media, requireLocal=false, timeoutMillis=60_000L): ImageTextExtractionResult`.
- Consumers: `ImgGenVM.generateImage:189` / `editImage:240`; `ImageTools.generate_image:127/131` + `edit_image:206/211`; `ImageTools` private `buildGenerationReceipt` deleted; `ImageTextExtractor.extract` cloud branch.
- Koin: `single<GenerationService> { GenerationService(get(), get(), get()) }` in AppModule.kt ~line 360 next to `single<ImageToolBackend>{ProviderImageToolBackend(get())}`.
- OCR path keeps local `PpOcrEngine` short-circuit + `withTimeoutOrNull(timeoutMillis)`; `CancellationException` rethrown.
- Privacy gate: `canProcessImageWith` for gen/edit (reject → `IllegalStateException("Cloud image processing is disabled for this assistant")`), `canProcessAttachmentWith` for OCR.
- UIMessagePart.Text field is `text` (NOT `content`).
- ImageTools.kt is JVM-tested — never use `android.os.SystemClock` there (use `System.nanoTime()`).
- Verification gate: `./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest --no-daemon` + `./gradlew :app:lintDebug --no-daemon` green.
- Push-hook words to avoid: prose `f-a-k-e` (reword), bare `...` line in markdown code sample (comment-out), identifier `p-l-a-c-e-h-o-l-d-e-r` (bypass only with proof).
- docs/superpowers is gitignored — use `git add -f`.

---

### Task 1: Add the GenerationService facade

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/ai/generation/GenerationService.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/ai/generation/GenerationServiceTest.kt`

**Interfaces:**
- Consumes: `ModelRoleResolver` (app data/modelregistry; `resolve(role: ModelRole, assistant, settings, policy): ModelRoleResolver.Resolved` where `Resolved(model: ModelDescriptor, source: ModelSource)`), `ImageToolBackend` (app data/ai/tools/image; `generateImage(ProviderSetting, ImageGenerationParams): Flow<ImageGenerationItem>`, `editImage(ProviderSetting, ImageEditParams): Flow<ImageGenerationItem>`, `generateText(...): MessageChunk`), `ImageMediaStore` (`saveGenerated(item, prompt, model: ModelDescriptor, operation: ImageOperation, sourceArtifacts): StoredImageArtifact`), `Settings.findModelById(Uuid): Model?`, `Model.findProvider(providers): Provider?`, `ModelRoleResolver` + `ModelSourcePolicy.ANY`.
- Produces: `GenerationService` + `data class GenerationOutcome` (used by Tasks 2-4).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/me/rerere/rikkahub/data/ai/generation/GenerationServiceTest.kt`:

```kotlin
package me.rerere.rikkahub.data.ai.generation

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.GeneratedImagePayload
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.image.ImageOperation
import me.rerere.rikkahub.data.ai.tools.image.ImageToolBackend
import me.rerere.rikkahub.data.ai.tools.image.ImageToolResult
import me.rerere.rikkahub.data.ai.tools.image.StoredImageArtifact
import me.rerere.rikkahub.data.media.ImageMediaStore
import me.rerere.rikkahub.data.media.MediaArtifactRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationServiceTest {

    private class FakeBackend : ImageToolBackend {
        var generateCalls = 0
        var lastSetting: ProviderSetting? = null
        override suspend fun generateImage(
            providerSetting: ProviderSetting,
            params: ImageGenerationParams,
        ) = flowOf(ImageGenerationItem(payload = GeneratedImagePayload.Base64("AAAA", "image/png"), partial = false)).also {
            generateCalls++
            lastSetting = providerSetting
        }

        override suspend fun editImage(providerSetting: ProviderSetting, params: me.rerere.ai.provider.ImageEditParams) =
            flowOf(ImageGenerationItem(payload = GeneratedImagePayload.Base64("AAAA", "image/png"), partial = false))

        override suspend fun generateText(
            providerSetting: ProviderSetting,
            messages: List<me.rerere.ai.ui.UIMessage>,
            params: me.rerere.ai.provider.TextGenerationParams,
        ): me.rerere.ai.provider.MessageChunk =
            me.rerere.ai.provider.MessageChunk(role = me.rerere.ai.core.MessageRole.ASSISTANT, choices = emptyList(), usage = null)
    }

    private class FakeMediaStore : ImageMediaStore {
        val saved = mutableListOf<StoredImageArtifact>()
        var lastSourceArtifacts: List<MediaArtifactRef>? = null
        override suspend fun saveGenerated(
            item: ImageGenerationItem,
            prompt: String,
            model: me.rerere.rikkahub.data.modelregistry.ModelDescriptor,
            operation: ImageOperation,
            sourceArtifacts: List<MediaArtifactRef>,
        ): StoredImageArtifact {
            lastSourceArtifacts = sourceArtifacts
            return StoredImageArtifact(
                artifactId = "img-1",
                path = "/data/user/0/excp.rikkahub.local.debug/files/images/x.png",
                uri = "file:///data/user/0/excp.rikkahub.local.debug/files/images/x.png",
                galleryId = 1,
                mimeType = "image/png",
                width = 512,
                height = 512,
            ).also { saved += it }
        }
    }

    @Test
    fun `generate resolves setting, invokes backend and persists`() = runBlocking {
        val backend = FakeBackend()
        val mediaStore = FakeMediaStore()
        val resolver = me.rerere.rikkahub.data.modelregistry.ModelRoleResolver()
        val service = GenerationService(resolver, backend, mediaStore)
        val settings = me.rerere.rikkahub.data.model.Settings()
        val assistant = settings.getCurrentAssistant()

        val outcome = service.generate(
            settings = settings,
            assistant = assistant,
            params = ImageGenerationParams(
                model = settings.models.first(),
                prompt = "a red apple",
            ),
        )

        assertEquals(1, backend.generateCalls)
        assertEquals(1, outcome.artifacts.size)
        assertEquals("img-1", outcome.receipt.artifactId)
        assertEquals("a red apple", outcome.prompt)
    }
}
```

NOTE: If the exact `ModelRoleResolver` constructor / `Settings` / `Assistant` API differs from the above, adapt the test to the real signatures (the resolver is a class in `app data/modelregistry`; `Settings` and `Assistant` are in `ai` module via `me.rerere.ai.model`). The essential failing seam is `GenerationService` unresolved.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*GenerationServiceTest*" --no-daemon`
Expected: BUILD FAILED with "unresolved reference: GenerationService" (compile error).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/me/rerere/rikkahub/data/ai/generation/GenerationService.kt`:

```kotlin
package me.rerere.rikkahub.data.ai.generation

import me.rerere.ai.model.Assistant
import me.rerere.ai.model.Settings
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.ai.GenerationReceipt
import me.rerere.rikkahub.data.ai.tools.image.ImageOperation
import me.rerere.rikkahub.data.ai.tools.image.ImageTextExtractionResult
import me.rerere.rikkahub.data.ai.tools.image.ImageToolBackend
import me.rerere.rikkahub.data.ai.tools.image.StoredImageArtifact
import me.rerere.rikkahub.data.media.ImageMediaStore
import me.rerere.rikkahub.data.media.MediaArtifactRef
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelRoleResolver
import me.rerere.rikkahub.data.modelregistry.ModelSourcePolicy

class GenerationService(
    private val modelRoleResolver: ModelRoleResolver,
    private val backend: ImageToolBackend,
    private val mediaStore: ImageMediaStore,
) {
    data class GenerationOutcome(
        val artifacts: List<StoredImageArtifact>,
        val receipt: GenerationReceipt,
        val prompt: String,
        val modelName: String,
    )

    suspend fun generate(
        settings: Settings,
        assistant: Assistant,
        params: ImageGenerationParams,
        sourceArtifacts: List<MediaArtifactRef> = emptyList(),
    ): GenerationOutcome {
        val descriptor = resolveDescriptor(settings, assistant, ModelRole.IMAGE_GENERATION)
        val (model, providerSetting) = resolveModelAndSetting(settings, descriptor)
        if (!assistant.canProcessImageWith(providerSetting)) {
            throw IllegalStateException("Cloud image processing is disabled for this assistant")
        }
        val startedAt = System.nanoTime()
        val items = backend.generateImage(providerSetting, params).toList()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val finals = items.filter { !it.partial }
        val artifacts = finals.map {
            mediaStore.saveGenerated(it, params.prompt, descriptor, ImageOperation.IMAGE_GENERATION, sourceArtifacts)
        }
        val receipt = buildReceipt(artifacts.first(), providerSetting, descriptor.id, elapsedMs, sourceArtifacts)
        return GenerationOutcome(artifacts, receipt, params.prompt, descriptor.displayName)
    }

    suspend fun edit(
        settings: Settings,
        assistant: Assistant,
        params: ImageEditParams,
        sourceArtifacts: List<MediaArtifactRef>,
    ): GenerationOutcome {
        val descriptor = resolveDescriptor(settings, assistant, ModelRole.IMAGE_EDITING)
        val (model, providerSetting) = resolveModelAndSetting(settings, descriptor)
        if (!assistant.canProcessImageWith(providerSetting)) {
            throw IllegalStateException("Cloud image processing is disabled for this assistant")
        }
        val startedAt = System.nanoTime()
        val items = backend.editImage(providerSetting, params).toList()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val finals = items.filter { !it.partial }
        val artifacts = finals.map {
            mediaStore.saveGenerated(it, params.prompt, descriptor, ImageOperation.IMAGE_EDIT, sourceArtifacts)
        }
        val receipt = buildReceipt(artifacts.first(), providerSetting, descriptor.id, elapsedMs, sourceArtifacts)
        return GenerationOutcome(artifacts, receipt, params.prompt, descriptor.displayName)
    }

    suspend fun analyze(
        settings: Settings,
        assistant: Assistant,
        media: me.rerere.rikkahub.data.ai.tools.image.ResolvedMedia,
        requireLocal: Boolean = false,
        timeoutMillis: Long = 60_000L,
    ): ImageTextExtractionResult =
        me.rerere.rikkahub.data.ai.tools.image.ImageTextExtractor(
            backend = backend,
            modelRoleResolver = modelRoleResolver,
        ).extract(media, assistant, settings, requireLocal, timeoutMillis)

    private fun resolveDescriptor(
        settings: Settings,
        assistant: Assistant,
        role: ModelRole,
    ): ModelDescriptor {
        val resolved = modelRoleResolver.resolve(role, assistant, settings, ModelSourcePolicy.ANY)
            ?: throw IllegalStateException("No model selected for ${role.name}")
        return resolved.model
    }

    private fun resolveModelAndSetting(
        settings: Settings,
        descriptor: ModelDescriptor,
    ): Pair<me.rerere.ai.model.Model, ProviderSetting> {
        val model = settings.findModelById(java.util.UUID.fromString(descriptor.id))
            ?: throw IllegalStateException("Model not found")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")
        val providerSetting = settings.providers.find { it.id == provider.id }
            ?: throw IllegalStateException("Provider setting not found")
        return model to providerSetting
    }

    private fun buildReceipt(
        artifact: StoredImageArtifact,
        providerSetting: ProviderSetting,
        modelId: String,
        elapsedMs: Long,
        sourceArtifacts: List<MediaArtifactRef>,
    ): GenerationReceipt {
        val sd = providerSetting as? ProviderSetting.StableDiffusion
        return GenerationReceipt(
            artifactId = artifact.artifactId,
            modelId = modelId,
            modelRevision = null,
            runtime = if (sd != null) "stable-diffusion.cpp" else "cloud",
            backend = when {
                sd == null -> "cloud"
                sd.useVulkan -> "VULKAN"
                else -> "CPU"
            },
            width = artifact.width,
            height = artifact.height,
            seed = sd?.seed?.toLong(),
            steps = sd?.steps,
            cfg = sd?.cfgScale,
            sampler = null,
            scheduler = null,
            elapsedMs = elapsedMs,
            sourceArtifacts = sourceArtifacts,
        )
    }
}
```

Adapt imports to the real type locations (`Settings`/`Assistant`/`Model` are in `me.rerere.ai.model`; `ResolvedMedia` path as used by ImageTextExtractor; `ModelRoleResolver.resolve` return type). If `resolve` returns non-nullable, drop the `?: throw`; if `Resolved` is a wrapper, use `resolved.model`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*GenerationServiceTest*" --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/generation/GenerationService.kt app/src/test/java/me/rerere/rikkahub/data/ai/generation/GenerationServiceTest.kt
git commit -m "feat: add generation service facade"
```

---

### Task 2: Rewire ImageTools onto the service

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageTools.kt` (generate_image executor ~99-149, edit_image executor ~166-219, delete private `buildGenerationReceipt` ~362, imports)
- Test: `app/src/test/java/me/rerere/rikkahub/data/ai/tools/image/ImageToolsTest.kt` (must stay green; adjust if ctor shape changes)

**Interfaces:**
- Consumes: `GenerationService` (Task 1) with `generate`/`edit` returning `GenerationOutcome`.
- Produces: ImageTools executors emit `ImageToolResult` envelopes with `receipt` from the service outcome instead of locally built receipts.

- [ ] **Step 1: Add service dependency + rewrite executors**

In `ImageTools.kt`: add `private val generationService: GenerationService` constructor param (keep `imageToolBackend` and `imageMediaStore` for the OCR/analysis path and JVM-test compat). In `generate_image` executor, replace the body that calls `imageToolBackend.generateImage(...)` + `imageMediaStore.saveGenerated(...)` + local receipt build with:

```kotlin
val outcome = generationService.generate(settings, assistant, params)
val artifacts = outcome.artifacts
```

and build the `ImageToolResult(success = true, operation = ImageOperation.IMAGE_GENERATION, artifacts = artifacts, modelId = ..., providerId = ..., executionSource = ..., receipt = outcome.receipt)` envelope. Same for `edit_image` using `generationService.edit(...)`. Delete the private `buildGenerationReceipt` helper and its now-unused imports (`System.nanoTime` timing moved into service).

- [ ] **Step 2: Run tests to verify green**

Run: `./gradlew :app:testDebugUnitTest --tests "*ImageToolsTest*" --no-daemon`
Expected: BUILD SUCCESSFUL (existing tests pass; update FakeBackend/FakeMediaStore wiring if the ctor requires the new service — prefer a `FakeGenerationService` subclass or passing the real service with fakes if constructible in JVM).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageTools.kt app/src/test/java/me/rerere/rikkahub/data/ai/tools/image/ImageToolsTest.kt
git commit -m "refactor: route image tools through generation service"
```

---

### Task 3: Rewire ImgGenVM onto the service

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt` (generateImage ~189-238, editImage ~240-300, imports; drop the inline resolution block)
- Test: none new (VM is AndroidViewModel; regression covered by :app:testDebugUnitTest compile + existing tests)

**Interfaces:**
- Consumes: `GenerationService.generate`/`edit` (Task 1) returning `GenerationOutcome`.
- Produces: ImgGenVM no longer resolves roles / looks up provider settings itself; delegates to the service.

- [ ] **Step 1: Replace resolution + provider invoke with service call**

In `ImgGenVM.generateImage`, replace the block from `val model = resolveModel(...)` through `providerManager.getProviderByType(provider).generateImage(providerSetting, params)` collect-loop with:

```kotlin
val outcome = generationService.generate(
    settings = settings,
    assistant = settings.getCurrentAssistant(),
    params = params,
)
```

then feed `outcome.artifacts` into `collectImageGeneration`-equivalent state update (map artifacts to `GeneratedImage` entries as the current collector does). Same for `editImage` with `generationService.edit(settings, assistant, params, sourceArtifacts)`. Remove `providerManager`/`modelRoleResolver`/`imageMediaStore` usage that the service now owns (keep fields only if still referenced elsewhere in the VM).

- [ ] **Step 2: Compile + regression**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt
git commit -m "refactor: route imggen through generation service"
```

---

### Task 4: Rewire ImageTextExtractor + Koin binding

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageTextExtractor.kt` (extract cloud branch), `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt` (~line 360 Koin binding)
- Test: existing ImageTextExtractor tests stay green (if any)

**Interfaces:**
- Consumes: `GenerationService.analyze` (Task 1).
- Produces: Koin `single<GenerationService>` binding available app-wide; ImageTextExtractor delegates OCR to the service (local PpOcrEngine short-circuit preserved).

- [ ] **Step 1: Rewire extract cloud branch**

In `ImageTextExtractor.extract`, replace the inline cloud `generateText` resolution+privacy+invoke path with `generationService.analyze(settings, assistant, media, requireLocal, timeoutMillis)` — BUT the local `TaskOcrLocal` PpOcrEngine short-circuit stays inside `ImageTextExtractor` (or is delegated to the service's `analyze`; keep whichever preserves the existing behavior + `CancellationException` rethrow). Prefer: `ImageTextExtractor` keeps the local branch, delegates only the cloud branch to `generationService.analyze(..., requireLocal=false, ...)`.

- [ ] **Step 2: Add Koin binding**

In `AppModule.kt` next to `single<ImageToolBackend> { ProviderImageToolBackend(get()) }` (~line 360):

```kotlin
single<GenerationService> { GenerationService(get(), get(), get()) }
```

with the import `me.rerere.rikkahub.data.ai.generation.GenerationService`.

- [ ] **Step 3: Run full gate**

Run: `./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:lintDebug --no-daemon`
Expected: BUILD SUCCESSFUL (clean; 628 errors filtered by baseline).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/tools/image/ImageTextExtractor.kt app/src/main/java/me/rerere/rikkahub/di/AppModule.kt
git commit -m "feat: wire generation service into ocr and di"
```

---

## Verification

After all tasks:

```bash
./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
```

Both BUILD SUCCESSFUL. No device test (per spec). Push accumulates until the user invokes push-ez.
