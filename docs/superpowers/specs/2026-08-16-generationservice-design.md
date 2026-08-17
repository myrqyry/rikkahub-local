# GenerationService Design

> **Status:** Approved design (user: 'Thin app-side facade', then 'Resolve+receipt in service')
> **Date:** 2026-08-16
> **Roadmap item:** GenerationService (architecture.md subsystem status: ❌ designed → this phase implements)

## Background

The roadmap's architecture diagram places a **Generation** executor branch that emits
`ArtifactRef` (multimodal generation). `GenerationService` is DESIGNED-ONLY in
`docs/references/architecture.md:158` — no code, no spec section. The generation
surface that exists today is spread across three consumers with duplicated
orchestration:

- `ImgGenVM.generateImage`/`editImage` (app/.../ui/pages/imggen/ImgGenVM.kt:189/:240) —
  resolves the IMAGE_GENERATION / IMAGE_EDITING model role, applies the privacy gate,
  invokes the provider via `providerManager.getProviderByType(provider)`, and persists
  via `imageMediaStore.saveGenerated`.
- `ImageTools` executors (app/.../data/ai/tools/image/ImageTools.kt) — `generate_image`
  (:127 + saveGenerated :131) and `edit_image` (:206 + saveGenerated :211) invoke the
  `ImageToolBackend` then persist; the private `buildGenerationReceipt` helper builds the
  receipt there.
- `ImageTextExtractor.extract` (cloud branch) — resolves the OCR role, applies
  `canProcessAttachmentWith`, invokes `generateText`, returns `ImageTextExtractionResult`.

These three consumers duplicate role resolution, privacy gating, and receipt assembly.
This phase consolidates them behind a single app-side `GenerationService` facade.

## Scope

**In scope:** a thin app-side `GenerationService` facade that owns the generation
pipeline (model resolution → privacy gate → provider invoke → persist → receipt) for
image generation, image editing, and OCR/text analysis. Consumer rewiring: `ImgGenVM`,
`ImageTools`, `ImageTextExtractor`. JVM facade tests. Koin binding.

**Out of scope:** moving the orchestration into local-llm/ai (pure JVM seams stay where
they are); integrating the canonical `ArtifactSink`/`ArtifactResolver` (local-llm) into
the persistence path; changing the `GenerationReceipt` type; `ProviderManager` /
`ProviderImageToolBackend` internals; the image-gen plan's capabilities/memory/eviction
behavior (all already shipped).

## Service Interface

File: `app/src/main/java/me/rerere/rikkahub/data/ai/generation/GenerationService.kt`

```kotlin
class GenerationService(
    private val modelRoleResolver: ModelRoleResolver,
    private val backend: ImageToolBackend,
    private val mediaStore: ImageMediaStore,
) {
    suspend fun generate(
        settings: Settings,
        assistant: Assistant,
        params: ImageGenerationParams,
        sourceArtifacts: List<MediaArtifactRef> = emptyList(),
    ): GenerationOutcome

    suspend fun edit(
        settings: Settings,
        assistant: Assistant,
        params: ImageEditParams,
        sourceArtifacts: List<MediaArtifactRef>,
    ): GenerationOutcome

    suspend fun analyze(
        settings: Settings,
        assistant: Assistant,
        media: ResolvedMedia,
        requireLocal: Boolean = false,
        timeoutMillis: Long = 60_000L,
    ): ImageTextExtractionResult
}

data class GenerationOutcome(
    val artifacts: List<StoredImageArtifact>,
    val receipt: GenerationReceipt,
    val prompt: String,
    val modelName: String,
)
```

### Ownership (user decision m1868)

The service takes `(role, settings, assistant, params, sourceArtifacts)` and performs
model resolution + privacy gate + receipt building INTERNALLY. Consumers pass intent
only, not pre-resolved providers. `ImageTools` drops its private `buildGenerationReceipt`;
`ImgGenVM` drops its resolution block. The service is the single source of truth for the
generation path.

## Data Flow

Each entry point follows the same pipeline, mirroring what the three consumers do today:

1. **Resolve role + model.** `modelRoleResolver.resolve(role, assistant, settings,
   ModelSourcePolicy.ANY)` → `ModelDescriptor`. Missing/bad resolution throws
   `IllegalStateException` (matching current `ImgGenVM.resolveModel` behavior).
2. **Resolve provider + setting.** `settings.findModelById(Uuid.parse(descriptor.model.id))`
   → ai `Model`; `model.findProvider(settings.providers)` → provider; find the
   `ProviderSetting` by id.
3. **Privacy gate.** For image generation/editing: `assistant.canProcessImageWith(
   providerSetting)` — rejection throws `IllegalStateException("Cloud image processing is
   disabled for this assistant")` (current `errorEnvelope("cloud_processing_blocked")`
   semantics). For OCR/analysis: `canProcessAttachmentWith` (current
   `ImageTextExtractor` behavior).
4. **Invoke.** `backend.generateImage(providerSetting, params).toList()` /
   `backend.editImage(providerSetting, params).toList()` / `backend.generateText(...)`
   (OCR path keeps its current `withTimeoutOrNull` + local `PpOcrEngine` short-circuit).
5. **Persist.** `mediaStore.saveGenerated(item, prompt, descriptor.model, operation,
   sourceArtifacts)` per non-partial item → `StoredImageArtifact` list.
6. **Receipt.** Build `GenerationReceipt` from the first artifact + resolved provider
   setting (reusing the exact logic of the current `ImageTools.buildGenerationReceipt`).
   Return `GenerationOutcome`.

## Consumer Rewiring

| Consumer | Today | After |
|---|---|---|
| `ImgGenVM.generateImage` (:189) | resolve + providerManager + collect + saveGenerated | `generationService.generate(settings, assistant, params)` |
| `ImgGenVM.editImage` (:240) | same for IMAGE_EDITING | `generationService.edit(...)` |
| `ImageTools.generate_image` (:127/:131) | backend.generateImage + saveGenerated + buildGenerationReceipt | `generationService.generate(...)` → artifacts/receipt → envelope |
| `ImageTools.edit_image` (:206/:211) | backend.editImage + saveGenerated + buildGenerationReceipt | `generationService.edit(...)` → artifacts/receipt → envelope |
| `ImageTools` (private `buildGenerationReceipt`) | in-file helper | deleted (moved into service) |
| `ImageTextExtractor.extract` cloud branch | resolve + privacy + generateText | `generationService.analyze(...)` |

Note: `ImageTools` keeps its `ImageToolBackend`/`ImageMediaStore` constructor params for
JVM-test compatibility only where the facade is not used; where the service is used, the
executor takes `GenerationService`. Final constructor shape decided at plan time to keep
existing `ImageToolsTest` stubs compiling.

## Koin Binding

`AppModule.kt` (region around line 360, next to
`single<ImageToolBackend> { ProviderImageToolBackend(get()) }`):

```kotlin
single<GenerationService> { GenerationService(get(), get(), get()) }
```

## Testing

JVM unit tests (`app/src/test/.../data/ai/generation/GenerationServiceTest.kt`) with a
`FakeBackend : ImageToolBackend` and `FakeMediaStore : ImageMediaStore` (mirroring the
stubs in `ImageToolsTest`):

1. `generate resolves, invokes, persists, and returns a receipt` — happy path: one
   artifact, receipt fields populated (runtime "stable-diffusion.cpp" or "cloud" from the
   fake setting, width/height from artifact, seed/steps/cfg from setting).
2. `generate rejects cloud when the assistant disables cloud processing` — throws
   `IllegalStateException`, backend never invoked.
3. `edit passes source artifacts through to persistence` — fake media store records the
   `sourceArtifacts` it received.
4. `analyze routes to local ocr when requireLocal` and `analyze routes to cloud
   generateText otherwise` — behavior preserved from `ImageTextExtractor`.

Verification gate: `./gradlew :app:testDebugUnitTest --no-daemon` and
`./gradlew :ai:testDebugUnitTest --no-daemon` (unchanged) plus `:app:lintDebug --no-daemon`.

## Out of Scope

- local-llm/ai orchestration move.
- `ArtifactSink`/`ArtifactResolver` integration into persistence (canonical ArtifactRef
  stays a seam; a future phase aligns `GenerationReceipt`/`ImageTools` onto it).
- `GenerationReceipt` type changes.
- Provider adapter / capabilities / memory / eviction behavior changes.
