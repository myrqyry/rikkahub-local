# Chat Multimodal Tools Design

**Date:** 2026-08-05
**PR:** 8
**Status:** Approved

## Goal

Expose four image-capable agent tools to chat — `analyze_image`, `extract_text_from_image`, `generate_image`, `edit_image` — resolving models through the model-role assignment system, enforcing assistant cloud-image privacy policy, persisting generated artifacts through a shared store, and rendering inline result cards with typed metadata.

## Architecture

```text
ImageTools
  → MediaInputResolver
  → ModelRoleResolver
  → ImageToolExecutionPlan
  → privacy check
  → ProviderManager
  → ImageMediaStore
  → artifact envelope + UIMessagePart.Image
```

Tools are **core model-capability tools**, always registered and exposed to eligible assistants regardless of the active chat model's capabilities. Tools do not depend on implicit "latest image" state; every operation receives an explicit `image_ref`, and every image-producing tool returns a stable saved reference. The chainable workflow is: `generate_image → edit_image → analyze_image → show_image → share`.

## Components

### MediaInputResolver

```kotlin
interface MediaInputResolver {
    suspend fun resolveImage(reference: String, executionContext: ToolExecutionContext): ResolvedMedia
}

data class ResolvedMedia(
    val stablePath: String,
    val originalReference: String,
    val mimeType: String,
    val sizeBytes: Long,
    val temporary: Boolean,
)
```

Resolution order: RikkaHub artifact ID → `file://` URI → `content://` URI → absolute path → workspace-relative path → structured failure. Build on the existing `ContentUriResolver` (handles SAF tree grants, MediaStore shares, direct `content://` grants). `content://` inputs are staged into a private temp file before inference because the grant may not survive cron/resume/background execution.

### ModelRoleResolver

Reuse the PR7 resolver. `resolve(role, assistant, settings, ModelSourcePolicy.ANY)` returns `ModelResolution`: `Resolved(model, source)`, `InvalidOverride`, `BlockedByPolicy`, `NoCompatibleModel`. Roles used: `OCR`, `VISION`, `IMAGE_GENERATION`, `IMAGE_EDITING`.

Privacy requirement: path resolution and model resolution are separate decisions. The chain before any cloud send is: resolve image → resolve model role → enforce assistant cloud-image policy → execute. A local file path does **not** imply local processing.

### ImageToolExecutionPlan

A descriptive execution record, not a service abstraction:

```kotlin
data class ImageToolExecutionPlan(
    val operation: ImageOperation,
    val model: ModelDescriptor,
    val providerSetting: ProviderSetting,
    val inputMedia: List<ResolvedMedia> = emptyList(),
    val sendsUserMedia: Boolean,
    val createsArtifact: Boolean,
    val source: ModelSource,
)
```

PR8 uses it for: privacy enforcement, logging, result metadata, approval explanation, and a future source-aware approval system. It does **not** own execution.

### ImageMediaStore

The most important extraction. Move all persistent image-output responsibilities out of `ImgGenVM.saveImageToStorage`:

```kotlin
interface ImageMediaStore {
    suspend fun saveGenerated(
        item: ImageGenerationItem,
        prompt: String,
        model: ModelDescriptor,
        operation: ImageOperation,
        sourceArtifacts: List<MediaArtifactRef>,
    ): StoredImageArtifact
}
```

Responsibilities:
- Stable filename generation
- Final image write
- MIME type and dimensions
- Gallery database registration (`GenMediaRepository.insertMedia`)
- Artifact ID creation
- Source-image lineage
- Returning file path and URI

Both `ImgGenVM` and the tools use the exact same path. Do **not** move preview-frame handling here; `ImgGenVM` retains its temporary preview behavior (tools save only completed outputs).

### ImageTextExtractor

Shared OCR execution so `OcrTransformer.performOcr` is not photocopied:

```kotlin
class ImageTextExtractor(
    private val providerManager: ProviderManager,
    private val modelRoleResolver: ModelRoleResolver,
)

suspend fun extract(
    media: ResolvedMedia,
    assistant: Assistant,
    settings: Settings,
): ImageTextExtractionResult
```

Both `OcrTransformer` (which adds caching and message replacement) and `extract_text_from_image` (which returns the raw structured result) share resolution, privacy, timeout, and provider invocation through this executor.

## Tool Contracts

`image_ref` field accepts: absolute filesystem path, `file://` URI, `content://` URI, RikkaHub artifact ID, or Image Studio media ID.

- `generate_image`: `{prompt, aspect_ratio: "1:1", count}` → one or more artifact envelopes.
- `edit_image`: `{image_ref, prompt}` — one primary image only for now; multi-reference editing deferred.
- `analyze_image`: `{image_ref, question?}` — `question` optional; absent = general description, present = targeted analysis. Role: `VISION`.
- `extract_text_from_image`: `{image_ref}` — literal transcription, layout preserved where practical. Role: `OCR`.

Analysis and OCR are distinct. `extract_text_from_image` output:

```json
{ "success": true, "text": "...", "language": "en", "image_ref": "artifact:img_123", "model_id": "...", "processing": "local" }
```

`analyze_image` output:

```json
{ "success": true, "analysis": "...", "image_ref": "...", "model_id": "...", "processing": "cloud" }
```

## Result Models

One result structure for generation and editing. Return one `UIMessagePart.Image` per artifact, then one text envelope.

```kotlin
@Serializable
data class ImageToolResult(
    val schemaVersion: Int = 1,
    val success: Boolean,
    val operation: ImageOperation,
    val artifacts: List<StoredImageArtifact> = emptyList(),
    val modelId: String? = null,
    val providerId: String? = null,
    val executionSource: String? = null,
    val error: ImageToolError? = null,
)

@Serializable
data class ImageToolError(
    val code: String,
    val detail: String? = null,
    val recovery: JsonObject? = null,
)

@Serializable
data class StoredImageArtifact(
    val artifactId: String,
    val path: String,
    val uri: String,
    val galleryId: Int,
    val mimeType: String,
    val width: Int,
    val height: Int,
)
```

### Typed result contract

`ImageToolResult` is the canonical typed result contract. Image tool renderers decode it directly from `ToolUIContext.content` using kotlinx serialization. Renderers must not parse display prose or search `UIMessagePart.Text` values. Extensible cross-module `PartMetadata` support is deferred because the current sealed metadata hierarchy and `ToolUIContext` do not expose an app-defined metadata path.

## Approval

Approval is **genuinely static** in PR8:

```text
analyze_image             AUTO
extract_text_from_image   AUTO
generate_image            ALWAYS_ASK
edit_image                ALWAYS_ASK
```

`needsApproval` runs before model resolution, so it cannot inspect the execution plan. Privacy enforcement still runs after resolution and before provider invocation. Do not pretend the approval system is source-aware until it is.

## Error Results

Structured failures, required codes:

```text
invalid_image_ref
image_not_found
unsupported_image_type
content_uri_not_granted
no_compatible_model
invalid_model_override
cloud_processing_blocked
provider_not_found
provider_failed
generation_returned_no_images
artifact_save_failed
```

Preserve the distinction between `no_compatible_model` and `cloud_processing_blocked` — a compatible cloud model exists but the assistant forbids cloud image processing — because they need different recovery actions.

When no compatible model exists, tools stay registered and return `{ "error": "no_compatible_model", "role": "<role>", "recovery": { "action": "manage_models", "tab": "image", "focus": "models" } }`. Silently removing the tool makes the agent think the capability does not exist.

## Inline Result Card

Ship these actions: **Open**, **Use as reference**, **Edit**, **Open in Image Studio**.

Defer: **Regenerate** (requires preserving all generation parameters — model, aspect ratio, count, seed, provider-specific body, reference images, editing mode) and **Save** (artifacts are already automatically saved and gallery-registered; Save would mean external-storage export).

## File Structure

```text
data/ai/tools/image/
  ImageTools.kt
  MediaInputResolver.kt
  ImageToolExecutionPlan.kt
  ImageToolResult.kt

data/media/
  ImageMediaStore.kt
```

Organize by responsibility; file count is not a quality metric.

## PR8 Boundary

### Include

- Four always-registered core tools
- Explicit `image_ref`
- Shared media resolver with `content://` temporary staging
- Small execution-plan model
- Model-role resolution
- Privacy enforcement
- Shared image persistence / gallery registration (`ImageMediaStore`)
- Shared OCR provider executor (`ImageTextExtractor`)
- Typed result contract (`ImageToolResult` decoded by renderers)
- Inline result card (Open / Use as reference / Edit / Open in Image Studio)
- Static approval mapping
- Unit and integration tests

### Defer

- Source-aware dynamic approval
- Cost estimation
- Per-capability tool toggles
- Conversation attachment aliases
- Multiple edit references
- Streaming generation previews inside chat
- Full `ImageGenerationService`
- Regeneration (unless request metadata is fully reproducible)

## Tests

1. Every tool is registered regardless of active chat-model capability.
2. `content://` input is staged before provider invocation.
3. Generated artifacts are registered in Image Studio.
4. Tool output path can be passed directly into another image tool.
5. Cloud-disabled analysis rejects a cloud vision model.
6. Cloud-disabled editing rejects before any provider call.
7. Local analysis proceeds without approval.
8. Generate and edit remain statically approval-gated.
9. OCR tool and `OcrTransformer` use the same execution component.
10. Failed gallery persistence does not return a fake successful artifact.
11. Multiple generated images each receive distinct artifact and gallery IDs.
12. JSON envelope decodes into the typed result with the same artifacts.
