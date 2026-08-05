# PR7 feature integrations design

PR7 connects feature-specific settings to the shared model capability registry
without migrating existing speech-provider storage. It adds explicit
assistant-level model and privacy controls while preserving current global
settings and chat-model behavior.

## Goals

PR7 must:

- expose capability-filtered selectors for image generation, image editing,
  vision, and OCR;
- keep TTS and ASR provider selection in the existing provider settings;
- provide deep links from feature settings to filtered Models views;
- support assistant-level registry model overrides;
- support assistant-level TTS and ASR provider overrides;
- prevent cloud processing when an assistant's privacy policy disallows it;
- preserve existing persisted IDs, provider storage, and global assignments.

PR7 does not migrate every TTS or ASR provider into `ModelRegistry`, replace
the existing chat `chatModelId` flow, or add a second model database.

## Persisted model and speech selections

Registry-backed assistant overrides use string-native IDs because local
inventory entries can use synthetic IDs such as
`local:<runtime>:<filename>`.

```kotlin
@Serializable
@JvmInline
value class RegistryModelId(val value: String)
```

`ModelRole` becomes serializable with explicit stable names. `Assistant`
gains the following fields:

```kotlin
val modelOverrides: Map<ModelRole, RegistryModelId> = emptyMap()
val ttsProviderOverrideId: Uuid? = null
val asrProviderOverrideId: Uuid? = null
val allowCloudAttachmentProcessing: Boolean = true
val allowCloudImageProcessing: Boolean = true
```

The defaults preserve existing behavior. `CHAT`, `TEXT_TO_SPEECH`, and
`SPEECH_TO_TEXT` are not stored in `modelOverrides`; chat keeps using
`chatModelId`, and speech uses the dedicated provider override fields.

## Resolution architecture

The existing pure `ModelResolver` remains the single registry resolution
algorithm. It is extended to apply source policy to every candidate, not only
automatic fallback candidates.

```kotlin
enum class ModelSourcePolicy { ANY, LOCAL_ONLY }
```

Resolution returns explicit failure states:

```kotlin
sealed interface ModelResolution {
    data class Resolved(
        val model: ModelDescriptor,
        val source: ResolutionSource,
    ) : ModelResolution

    data class InvalidOverride(
        val modelId: String,
        val reason: FailureReason,
    ) : ModelResolution

    data class BlockedByPolicy(
        val modelId: String,
        val policy: ModelSourcePolicy,
    ) : ModelResolution

    data object NoCompatibleModel : ModelResolution
}
```

`ModelRoleResolver` is a stateful facade. It gathers the assistant,
conversation, global assignment, registry snapshot, and source policy, then
delegates registry-backed roles to `ModelResolver`.

The precedence is:

1. A valid assistant override resolves directly.
2. A missing, disabled, incompatible, or policy-blocked assistant override
   returns an explicit failure and never silently falls through.
3. Without an assistant override, use the global assignment.
4. An invalid global assignment may use automatic fallback when policy allows.
5. Automatic fallback uses ready, enabled models with verified capabilities,
   preferring local models before cloud models.
6. If no model qualifies, return `NoCompatibleModel`.

`LOCAL_ONLY` rejects cloud assistant overrides, cloud global assignments, and
cloud automatic candidates.

## Capability confidence

Verified and inferred capabilities remain separate. Add these predicates:

```kotlin
fun ModelDescriptor.canAutoResolve(capability: ModelCapability): Boolean
fun ModelDescriptor.canExplicitlySelect(capability: ModelCapability): Boolean
```

Automatic resolution accepts only verified capabilities. Explicit selectors
may show and select unverified capabilities with an `Unverified` badge.
Provider-specific capability metadata may promote a capability to verified.
Image editing is selectable only when an actual provider or runtime signal
advertises it.

## Speech compatibility bridge

TTS and ASR continue to use `Settings.ttsProviders`,
`Settings.selectedTTSProviderId`, `Settings.asrProviders`, and
`Settings.selectedASRProviderId`.

`SpeechSelectionFacade` resolves each provider independently:

1. Assistant provider override.
2. Global selected provider.
3. First available provider.
4. No provider configured.

Deleting an overridden provider falls back to the global provider. Updating an
assistant override never changes the global selection.

## Feature integrations

Image generation and editing selectors query registry capabilities. Editing
first resolves `IMAGE_EDITING`; when no explicit editing assignment exists, it
falls back to the legacy image-generation assignment only when that selected
provider advertises editing support. PR7 does not select an unrelated first
editing model.

Vision and OCR selectors query their matching registry capabilities. Inferred
models remain visible with an `Unverified` badge.

Speech settings keep the existing provider selectors and add links to the
existing speech configuration surface.

Feature pages link to Models using `ModelsPageRequest` with the relevant tab,
focus, and model ID. Request state must survive process recreation.

## Assistant controls

The assistant detail page adds an AI capabilities section containing:

- registry-backed overrides for vision, OCR, image generation, and image
  editing;
- TTS and ASR provider overrides;
- `Allow cloud attachment processing`;
- `Allow cloud image processing`.

The selectors show only compatible models or providers and expose actionable
links to manage missing selections.

## Attachment privacy enforcement

`allowCloudAttachmentProcessing` applies to both raw attachments and content
derived from attachments. The active assistant from `TransformerContext` is
used for enforcement.

When the chat model is local, direct image input is allowed. When it is cloud
and attachment processing is disabled, the transformer must resolve a local
vision/OCR processor. If none exists, it returns an actionable failure instead
of sending either the original attachment or derived OCR/document text to the
cloud chat model.

`allowCloudImageProcessing` applies to cloud image generation, editing, and
uploaded reference images. A blocked operation returns a policy result that
the UI can explain and link to assistant settings.

## Tests

PR7 adds tests for:

- synthetic local registry IDs and serialisation compatibility;
- missing new `Assistant` fields decoding with current defaults;
- strict assistant override failure semantics;
- local-only rejection of cloud assistant and global assignments;
- local-first automatic fallback;
- unverified selector visibility without automatic selection;
- image-editing fallback to the legacy generation assignment;
- direct and derived attachment privacy enforcement;
- TTS and ASR override fallback and global-selection isolation;
- unchanged `chatModelId` behavior;
- Models deep links restoring tab and focus after recreation.

## Verification

Run focused model, resolver, assistant, and transformer tests first. Then run:

```bash
./gradlew test assembleDebug --no-daemon
git diff --check
```

Do not stage `SESSION-STATE.md`.
