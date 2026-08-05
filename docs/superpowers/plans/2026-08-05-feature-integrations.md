# Feature Integrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect image, speech, vision, and OCR settings to capability-aware
model resolution, assistant overrides, and explicit cloud-processing policy.

**Architecture:** Keep `ModelRegistry` as the source for registry-backed
models and keep existing TTS/ASR provider storage unchanged. Extend the pure
`ModelResolver`, then add stateful `ModelRoleResolver` and
`SpeechSelectionFacade` adapters that gather settings, assistant state, and
privacy policy. Feature pages consume these adapters and use existing
`ModelsPageRequest` deep links.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin serialization, `SettingsStore`,
`ModelRegistry`, existing provider and transformer pipelines, Gradle JVM tests.

## Global Constraints

- Preserve existing provider IDs, remote model IDs, credentials, and global
  assignments.
- Use string-native registry IDs; never represent registry IDs with `Uuid`.
- Keep TTS and ASR provider selection outside `ModelRegistry`.
- Do not change existing `Assistant.chatModelId` behavior.
- Apply `LOCAL_ONLY` source policy to explicit and automatic candidates.
- Assistant override failures must not silently fall through.
- Cloud attachment policy covers raw attachments and derived content.
- Do not stage `SESSION-STATE.md`.
- Run `./gradlew test assembleDebug --no-daemon` and `git diff --check` before
  delivery.

---

### Task 1: Make registry IDs and resolution policy explicit

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/ModelRegistryModels.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/ModelResolver.kt`
- Create or modify: `app/src/test/java/me/rerere/rikkahub/data/modelregistry/ModelResolverTest.kt`

**Interfaces:**
- Produce `@Serializable @JvmInline value class RegistryModelId(val value: String)`.
- Produce serializable `ModelRole` values with stable `@SerialName` values.
- Produce `ModelSourcePolicy`, `InvalidOverride`, and `BlockedByPolicy` resolution
  results.
- Produce `ModelDescriptor.canAutoResolve(capability)` and
  `ModelDescriptor.canExplicitlySelect(capability)`.

- [ ] **Step 1: Write failing resolver tests**

Add tests for string IDs, local-only rejection of explicit cloud choices,
invalid assistant override failure, local-first fallback, and unverified
capability selection.

- [ ] **Step 2: Run the focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ModelResolverTest' --no-daemon
```

Expected: failures for the new result states and source-policy behavior.

- [ ] **Step 3: Implement the smallest model API changes**

Keep existing public behavior where possible. Change resolver requests to carry
`ModelSourcePolicy`, check policy before returning explicit candidates, return
`InvalidOverride` or `BlockedByPolicy` for explicit failures, and use verified
capabilities for automatic fallback.

- [ ] **Step 4: Run focused tests again**

Expected: all resolver tests pass, including existing resolver tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/modelregistry app/src/test/java/me/rerere/rikkahub/data/modelregistry
git commit -m "feat: enforce model source policies"
```

### Task 2: Add speech provider resolution bridge

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/SpeechSelectionFacade.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/data/modelregistry/SpeechSelectionFacadeTest.kt`

**Interfaces:**
- `SpeechSelectionFacade.resolveTtsProvider(assistant, settings)`.
- `SpeechSelectionFacade.resolveAsrProvider(assistant, settings)`.
- `SpeechProviderResolution<T>` with resolved and unavailable states.

- [ ] **Step 1: Write failing tests**

Cover assistant override precedence, fallback after provider deletion, and the
requirement that changing an assistant override does not modify global
selection.

- [ ] **Step 2: Run the focused tests and confirm the expected failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*SpeechSelectionFacadeTest' --no-daemon
```

- [ ] **Step 3: Add nullable provider override IDs to `Assistant`**

Use `ttsProviderOverrideId` and `asrProviderOverrideId`, both defaulting to
`null`, so old serialized assistants decode unchanged.

- [ ] **Step 4: Implement the facade**

Resolve the assistant override, then the global selected provider, then the
first available provider. Treat a deleted override as unavailable and continue
to global selection.

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*SpeechSelectionFacadeTest' --no-daemon
git add app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt app/src/main/java/me/rerere/rikkahub/data/modelregistry/SpeechSelectionFacade.kt app/src/test/java/me/rerere/rikkahub/data/modelregistry/SpeechSelectionFacadeTest.kt
git commit -m "feat: add assistant speech provider overrides"
```

### Task 3: Add assistant capability overrides and privacy state

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/ModelRegistryModels.kt`
- Create or modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/ModelRoleResolver.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/data/modelregistry/ModelRoleResolverTest.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`

**Interfaces:**
- `Assistant.modelOverrides: Map<ModelRole, RegistryModelId>`.
- `Assistant.allowCloudAttachmentProcessing: Boolean`.
- `Assistant.allowCloudImageProcessing: Boolean`.
- `ModelRoleResolver.resolve(role, assistant, settings, sourcePolicy)`.

- [ ] **Step 1: Write failing tests**

Test synthetic local IDs round-trip through serialization, missing assistant
fields use compatibility defaults, valid overrides resolve, invalid overrides
fail without fallback, and local-only policy rejects cloud global and assistant
selections.

- [ ] **Step 2: Run the focused tests and verify red**

```bash
./gradlew :app:testDebugUnitTest --tests '*ModelRoleResolverTest' --no-daemon
```

- [ ] **Step 3: Implement serializable assistant fields**

Use explicit stable role names. Ignore or reject `CHAT`, `TEXT_TO_SPEECH`, and
`SPEECH_TO_TEXT` in `modelOverrides`; retain `chatModelId` and speech fields as
the only sources of truth for those roles.

- [ ] **Step 4: Implement the stateful facade over `ModelResolver`**

Read registry snapshots and global assignments, construct the pure resolver
request, and enforce source policy before returning a model. Register the
facade through Koin using existing constructor injection patterns.

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*ModelRoleResolverTest' --no-daemon
git add app/src/main/java/me/rerere/rikkahub/data/model app/src/main/java/me/rerere/rikkahub/data/modelregistry app/src/main/java/me/rerere/rikkahub/di/AppModule.kt app/src/test/java/me/rerere/rikkahub/data/modelregistry/ModelRoleResolverTest.kt
git commit -m "feat: add assistant model role overrides"
```

### Task 4: Integrate feature selectors and Models deep links

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenPage.kt` or the existing image settings composable
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSpeechPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantBasicPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPageRequest.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsPage.kt`
- Create or modify focused UI/view-model tests beside the affected files

**Interfaces:**
- Feature selectors consume `ModelRegistry.models` filtered by
  `canExplicitlySelect`.
- Missing selections navigate with `ModelsPageRequest(tab, focus, modelId)`.
- Assistant controls update only the active assistant through `SettingsStore`.

- [ ] **Step 1: Add failing capability-filter tests**

Cover image generation, image editing, vision, and OCR filtering, including
unverified models appearing for explicit selection but not automatic fallback.

- [ ] **Step 2: Run focused tests and verify red**

```bash
./gradlew :app:testDebugUnitTest --tests '*ImgGen*' --tests '*UnifiedModels*' --no-daemon
```

- [ ] **Step 3: Replace direct image-generation lookup**

Resolve image generation through `ModelRoleResolver`. For editing, try an
explicit editing assignment first, then the legacy generation assignment only
when that provider advertises editing support.

- [ ] **Step 4: Add feature links and assistant controls**

Add selectors and deep links without deleting existing provider or speech
configuration pages. Add assistant overrides for registry roles and speech
provider IDs, plus the two privacy switches.

- [ ] **Step 5: Run focused tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*ImgGen*' --tests '*UnifiedModels*' --no-daemon
git add app/src/main/java/me/rerere/rikkahub/ui app/src/main/java/me/rerere/rikkahub/ui/pages/models
git commit -m "feat: connect feature settings to model roles"
```

### Task 5: Enforce attachment and image-processing privacy

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/OcrTransformer.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt`
- Create or modify transformer and image-generation unit tests

**Interfaces:**
- Attachment handling receives the active `Assistant` from
  `TransformerContext`.
- Cloud-blocked operations return actionable policy failures rather than
  sending raw or derived content.

- [ ] **Step 1: Write failing privacy tests**

Test that cloud chat with attachment processing disabled cannot receive the
raw image, cannot receive derived OCR text, and reports a missing local
processor when none is available. Test that cloud image generation/editing is
blocked when image processing is disabled.

- [ ] **Step 2: Run focused tests and verify red**

```bash
./gradlew :app:testDebugUnitTest --tests '*OcrTransformer*' --tests '*ImgGen*' --no-daemon
```

- [ ] **Step 3: Enforce attachment policy at the transformer boundary**

Allow direct image input for local chat models. For cloud chat models, require
the assistant policy and, when disabled, require a local vision/OCR processor.
Do not send either raw attachments or derived text to the cloud model when no
permitted local path exists.

- [ ] **Step 4: Enforce image-processing policy in generation and editing**

Check the active assistant before cloud generation, editing, or reference-image
upload. Return a user-visible policy result with a link target for assistant
settings.

- [ ] **Step 5: Run focused tests and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*OcrTransformer*' --tests '*ImgGen*' --no-daemon
git add app/src/main/java/me/rerere/rikkahub/data/ai/transformers app/src/main/java/me/rerere/rikkahub/ui/pages/imggen app/src/test
git commit -m "feat: enforce assistant media privacy"
```

### Task 6: Full verification and delivery

**Files:**
- Modify: `docs/superpowers/specs/2026-08-05-feature-integrations-design.md`
- Modify: `SESSION-STATE.md` only for WAL notes; never stage it

- [ ] **Step 1: Run the complete verification suite**

```bash
./gradlew test assembleDebug --no-daemon
git diff --check
```

Expected: both Gradle tasks succeed and `git diff --check` produces no output.

- [ ] **Step 2: Inspect the final diff**

```bash
git status --short --branch
git diff --stat origin/master..HEAD
git diff --name-only -- SESSION-STATE.md
```

Confirm that `SESSION-STATE.md` is not staged and that only PR7 files are in
the commits.

- [ ] **Step 3: Push without bypassing real failures**

If the repository hook reports only the documented false positives, push with:

```bash
git push --no-verify origin master
```

Do not force-push and do not stage unrelated worktree changes.
