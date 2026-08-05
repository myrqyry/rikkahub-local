# Unified Models Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `Screen.SettingModels` with one registry-backed Models page that combines assignments, capability-filtered inventory, and links to existing lifecycle/configuration pages without changing persisted model identities.

**Architecture:** Keep the PR5 registry in `app/.../data/modelregistry`. Add a small legacy assignment adapter for Title and Translation, a `UnifiedModelsViewModel` for derived UI state, and focused Compose components under `ui/pages/models`. The existing route remains `Screen.SettingModels`; only its entry composable changes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX lifecycle Compose, Koin, Kotlin Coroutines/StateFlow, JUnit4 JVM tests, existing `SettingsStore`, `ModelRegistry`, and navigation abstractions.

## Global Constraints

- Preserve provider IDs, remote model IDs, credentials, existing assistant model IDs, and existing persisted defaults.
- Do not add a second model database or rewrite provider/local-runtime storage.
- `Screen.SettingModels` remains the single user-facing Models destination.
- Vision and OCR are distinct assignments even when a model supports both.
- Title and Translation remain editable through a compatibility adapter.
- Lifecycle operations remain owned by existing provider/local-management pages.
- Do not silently replace a disabled, missing, or incompatible selected model.
- Cloud fallback remains opt-in through the existing resolver policy.
- Do not expose a registry `install()` no-op as a successful UI action.
- Never stage `.superpowers/` or unrelated `SESSION-STATE.md` changes.

---

## File Map

- Create: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/LegacyModelAssignmentAdapter.kt` — StateFlow adapter for existing Title and Translation settings.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPageRequest.kt` — tab/focus/provider/model request state.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModel.kt` — registry collection, filters, assignment actions, repair state, and navigation intents.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsPage.kt` — scaffold, header, assignment section, tabs, and inventory sections.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelAssignmentsSection.kt` — grouped assignment rows and compatible selectors.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelCapabilityTabs.kt` — All/Chat/Vision/Image/Speech/Embeddings tabs.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelCard.kt` — descriptor card, capability badges, lifecycle, assignment roles, and detail action.
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/CloudProviderCard.kt` — collapsible provider group and provider actions.
- Create: `app/src/test/java/me/rerere/rikkahub/data/modelregistry/LegacyModelAssignmentAdapterTest.kt` — adapter read/write/clear tests.
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModelTest.kt` — filtering, assignment parity, and repair-state tests.
- Modify: `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt:503-505` — render `UnifiedModelsPage()` for `Screen.SettingModels`.
- Retain: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt` during implementation for prompt settings and parity extraction; remove only after the route no longer depends on it and prompt settings have an explicit destination or are intentionally retained elsewhere.

## Interfaces

The registry interfaces already available to implementation tasks are:

```kotlin
interface ModelRegistry {
    val models: StateFlow<List<ModelDescriptor>>
    val providers: StateFlow<List<ModelProviderDescriptor>>
    val assignments: StateFlow<ModelAssignments>
    suspend fun refreshProvider(providerId: String)
    suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean)
    suspend fun assign(role: ModelRole, modelId: String?)
    suspend fun install(modelId: String)
    suspend fun remove(modelId: String)
}
```

The adapter must expose:

```kotlin
interface LegacyModelAssignmentAdapter {
    val titleModelId: StateFlow<String?>
    val translationModelId: StateFlow<String?>
    suspend fun setTitleModel(modelId: String?)
    suspend fun setTranslationModel(modelId: String?)
}
```

The UI model uses these request types:

```kotlin
enum class ModelsFocus { ASSIGNMENTS, MODELS }

data class ModelsPageRequest(
    val initialTab: ModelTab = ModelTab.ALL,
    val focus: ModelsFocus? = null,
    val providerId: String? = null,
    val modelId: String? = null,
)
```

The assignment component keeps the registry roles and legacy utility roles in
one visual surface:

```kotlin
@Composable
fun ModelAssignmentsSection(
    assignments: ModelAssignments,
    legacyAssignments: LegacyAssignmentsUiState,
    availableModels: List<ModelDescriptor>,
    onAssignmentChanged: (ModelRole, String?) -> Unit,
    onLegacyAssignmentChanged: (LegacyAssignmentKey, String?) -> Unit,
)
```

`LegacyAssignmentsUiState` contains `titleModelId` and `translationModelId`;
`LegacyAssignmentKey` contains exactly `TITLE` and `TRANSLATION`.

---

### Task 1: Add the legacy assignment adapter

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/LegacyModelAssignmentAdapter.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/modelregistry/LegacyModelAssignmentAdapterTest.kt`
- Inspect: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:104` and `:635`
- Inspect: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingVM.kt`

**Interfaces:**
- Consumes `SettingsStore.settingsFlow`, `Settings.titleModelId`, and `Settings.translateModeId`.
- Produces `LegacyModelAssignmentAdapter` with StateFlow values and atomic `SettingsStore.update` writes.

- [ ] **Step 1: Write failing adapter tests**

Test the exact persisted fields and null semantics:

```kotlin
@Test
fun readsTitleAndTranslationIds() = runTest {
    val store = fakeSettingsStore(Settings(titleModelId = uuidA, translateModeId = uuidB))
    val adapter = SettingsLegacyModelAssignmentAdapter(store)
    assertEquals(uuidA.toString(), adapter.titleModelId.first())
    assertEquals(uuidB.toString(), adapter.translationModelId.first())
}

@Test
fun writesAndClearsBothUtilityAssignments() = runTest {
    val store = fakeSettingsStore(Settings())
    val adapter = SettingsLegacyModelAssignmentAdapter(store)
    adapter.setTitleModel(uuidA.toString())
    adapter.setTranslationModel(uuidB.toString())
    assertEquals(uuidA, store.settingsFlow.first().titleModelId)
    assertEquals(uuidB, store.settingsFlow.first().translateModeId)
    adapter.setTitleModel(null)
    adapter.setTranslationModel(null)
    assertNull(store.settingsFlow.first().titleModelId)
    assertNull(store.settingsFlow.first().translateModeId)
}
```

Use the repository's existing `SettingsStore` test construction pattern; do not
add a new persistence abstraction.

- [ ] **Step 2: Run the focused tests and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*LegacyModelAssignmentAdapterTest' --no-daemon
```

Expected: FAIL because the adapter and fake-store test helper do not exist.

- [ ] **Step 3: Implement the adapter**

Implement `SettingsLegacyModelAssignmentAdapter` with `map`-derived flows:

```kotlin
class SettingsLegacyModelAssignmentAdapter(
    private val settingsStore: SettingsStore,
) : LegacyModelAssignmentAdapter {
    override val titleModelId = settingsStore.settingsFlow
        .map { it.titleModelId?.toString() }
        .distinctUntilChanged()
    override val translationModelId = settingsStore.settingsFlow
        .map { it.translateModeId?.toString() }
        .distinctUntilChanged()

    override suspend fun setTitleModel(modelId: String?) {
        settingsStore.update { it.copy(titleModelId = modelId?.let(Uuid::parse)) }
    }

    override suspend fun setTranslationModel(modelId: String?) {
        settingsStore.update { it.copy(translateModeId = modelId?.let(Uuid::parse)) }
    }
}
```

Reject malformed non-null UUID strings with the same explicit validation style
used by the registry; do not convert malformed IDs to null.

- [ ] **Step 4: Run the adapter tests**

Run the command from Step 2. Expected: all adapter tests PASS.

- [ ] **Step 5: Commit the adapter**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/modelregistry/LegacyModelAssignmentAdapter.kt app/src/test/java/me/rerere/rikkahub/data/modelregistry/LegacyModelAssignmentAdapterTest.kt
git commit -m "feat: adapt legacy model assignments"
```

### Task 2: Build the unified page view model

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPageRequest.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModel.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModelTest.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt` only if constructor injection cannot resolve existing Koin bindings.

**Interfaces:**
- Consumes `ModelRegistry`, `LegacyModelAssignmentAdapter`, and `ModelsPageRequest`.
- Produces lifecycle-aware StateFlows for visible models, selected tab, filters,
  assignments, legacy assignments, and repair state.

- [ ] **Step 1: Write failing pure view-model tests**

Cover exact derived behavior:

```kotlin
@Test
fun filtersByCapabilityAndSearchWithoutChangingRegistryInventory() = runTest {
    val vm = viewModelWith(models = listOf(chat, vision, image))
    vm.setTab(ModelTab.VISION)
    vm.setSearch("camera")
    assertEquals(listOf(vision), vm.visibleModels.first())
    assertEquals(listOf(chat, vision, image), vm.allModels.first())
}

@Test
fun assignmentsPreserveTitleTranslationAndDistinctVisionOcr() = runTest {
    val vm = viewModelWith(assignments = assignments, legacy = legacy)
    assertEquals(chat.id, vm.assignments.first().defaults[ModelRole.CHAT])
    assertEquals(vision.id, vm.assignments.first().defaults[ModelRole.VISION])
    assertEquals(ocr.id, vm.assignments.first().defaults[ModelRole.OCR])
    assertEquals(titleId, vm.legacyAssignments.first().titleModelId)
    assertEquals(translationId, vm.legacyAssignments.first().translationModelId)
}

@Test
fun selectedUnavailableModelProducesRepairStateInsteadOfReplacement() = runTest {
    val vm = viewModelWith(selectedChat = missingId, models = listOf(chat))
    assertEquals(RepairState.ModelUnavailable(ModelRole.CHAT, missingId), vm.repairState.first())
}
```

Add cases for disabled providers, disabled capabilities, local readiness, source
filters, provider filters, and explicit clear operations.

- [ ] **Step 2: Run tests and confirm failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*UnifiedModelsViewModelTest' --no-daemon
```

Expected: FAIL because `ModelsPageRequest`, the view model, and derived state
types do not exist.

- [ ] **Step 3: Implement request and derived state**

Implement the exact tab mapping:

```kotlin
fun ModelTab.capability(): ModelCapability? = when (this) {
    ModelTab.ALL -> null
    ModelTab.CHAT -> ModelCapability.CHAT
    ModelTab.VISION -> ModelCapability.VISION
    ModelTab.IMAGE -> ModelCapability.IMAGE_GENERATION
    ModelTab.SPEECH -> null
    ModelTab.EMBEDDINGS -> ModelCapability.EMBEDDINGS
}
```

Speech matches `TEXT_TO_SPEECH`, `SPEECH_TO_TEXT`, or
`AUDIO_UNDERSTANDING`. Visible models must match search text, source filter,
provider filter, and selected tab. Keep `allModels` separate from
`visibleModels` so filtering cannot alter registry state.

Implement `assign(role, modelId)` as a coroutine operation that validates the
descriptor with `supports(role.capability())` and enabled/provider/lifecycle
state before calling `ModelRegistry.assign`. Route Title/Translation keys to
the legacy adapter. Store failures in `operationError`; retain repair state
when the selected descriptor is unusable.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*UnifiedModelsViewModelTest' --no-daemon
```

Expected: all view-model tests PASS.

- [ ] **Step 5: Commit the view model**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPageRequest.kt app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModel.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModelTest.kt
git commit -m "feat: add unified models view state"
```

### Task 3: Extract the assignment section

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelAssignmentsSection.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt` only to extract or remove duplicated assignment composables after parity is established.
- Reuse: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`, `app/src/main/java/me/rerere/rikkahub/ui/components/ui/CardGroup.kt`.

**Interfaces:**
- Consumes `ModelAssignments`, `LegacyAssignmentsUiState`, and filtered `ModelDescriptor` values.
- Produces assignment rows for Conversation, Media, Knowledge, and Utility
  groups; all changes flow through view-model callbacks.

- [ ] **Step 1: Add a component-level parity test seam**

Keep selection logic in a pure helper so JVM tests can verify compatibility:

```kotlin
internal fun compatibleAssignments(
    role: ModelRole,
    models: List<ModelDescriptor>,
): List<ModelDescriptor> = models.filter {
    it.providerEnabled && it.supports(role.capability()) &&
        (it.source !is ModelSource.Local || it.lifecycle == ModelLifecycle.READY || it.installed)
}
```

Test Chat, Vision, OCR, Image Generation, and Embeddings independently. Assert
that a model supporting both Vision and OCR appears in both selectors, while a
vision-only model does not appear in the OCR selector unless the explicit OCR
fallback policy is enabled by the caller.

- [ ] **Step 2: Implement the grouped section**

Render one `CardGroup` surface with rows in this order:

```text
Conversation: Chat, Vision, OCR
Media: Image generation
Knowledge: Embeddings
Utility models: Title generation, Translation
```

Each row opens a registry-descriptor selector scoped to its role, shows the
current display name or a clear empty state, and offers clear only where the
existing setting allows clearing. Use `ModelListSheet` patterns for search and
provider grouping, but do not pass legacy `Uuid`/`ProviderSetting` values into
the new registry selector.

Show repair state inline when the current assignment ID has no compatible
descriptor. Do not call `ModelRegistry.install` or `refreshProvider` from a
selector.

- [ ] **Step 3: Run focused tests and compile**

```bash
./gradlew :app:testDebugUnitTest --tests '*ModelAssignments*' --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: focused tests PASS and Kotlin compilation succeeds.

- [ ] **Step 4: Commit the assignment extraction**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelAssignmentsSection.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models
git commit -m "feat: extract unified model assignments"
```

### Task 4: Add inventory UI and replace the route destination

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsPage.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelCapabilityTabs.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelCard.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/CloudProviderCard.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt:503-505`
- Reuse: `app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerPage.kt`, provider detail routes, `AutoAIIcon`, `Tag`, and existing theme tokens.

**Interfaces:**
- Consumes `UnifiedModelsViewModel` StateFlows and navigation intents.
- Produces the only user-facing Models page for `Screen.SettingModels`.

- [ ] **Step 1: Implement capability tabs**

Use a single selected-tab state and stable labels:

```kotlin
@Composable
fun ModelCapabilityTabs(
    selected: ModelTab,
    onSelected: (ModelTab) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        ModelTab.entries.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                text = { Text(tab.label()) },
            )
        }
    }
}
```

Use existing localized strings where available and add only missing strings to
all supported locale resources. Do not hard-code user-facing copy in cards.

- [ ] **Step 2: Implement model/provider cards**

`ModelCard` must render descriptor ID-independent content: display name, source,
lifecycle, enabled state, capability badges, unverified markers, assignment
roles, and a detail action. For cloud descriptors, navigate to
`Screen.SettingProviderDetail(providerId)`. For local descriptors, navigate to
the existing local model/runtime management page. The card must not invent an
install success state.

`CloudProviderCard` must collapse/expand, show provider enabled/connection
state, list its filtered model cards, and expose Provider Settings plus catalog
refresh actions. Refresh invokes `viewModel.refreshProvider(providerId)` and
surfaces an error without clearing existing models.

- [ ] **Step 3: Compose the unified page**

Use one `Scaffold` with the existing `BackButton` and Material 3 top bar. The
content order is:

```text
Models header
ModelAssignmentsSection
ModelCapabilityTabs
Search/source/provider filters
Local Models
Cloud Models
```

Honor `ModelsPageRequest.focus` by scrolling/focusing Assignments or Models on
first composition. Honor `providerId`/`modelId` by expanding and scrolling to
the requested group/card when present, without changing the route identity.

- [ ] **Step 4: Switch the route**

Replace the route entry only:

```kotlin
entry<Screen.SettingModels> {
    UnifiedModelsPage()
}
```

Remove the route import for `SettingModelPage` only after no other caller uses
it. Do not add a second route or leave a visible legacy link.

- [ ] **Step 5: Run UI compile and tests**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: all app JVM tests PASS and compilation succeeds with only existing
warnings.

- [ ] **Step 6: Commit the page and route replacement**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models app/src/main/java/me/rerere/rikkahub/RouteActivity.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt app/src/main/res
git commit -m "feat: replace settings model page with unified models"
```

### Task 5: Full verification and migration audit

**Files:**
- Modify only if tests expose parity defects: the files from Tasks 1-4.
- Do not stage: `SESSION-STATE.md`, `.superpowers/`, build outputs, or unrelated changes.

**Interfaces:**
- Consumes the complete PR6 implementation.
- Produces verified route, assignment, filtering, and identity compatibility.

- [ ] **Step 1: Audit persisted assignment coverage**

Search every existing `Settings` assignment used by `SettingModelPage`:

```bash
git grep -n -E 'chatModelId|fastModelId|titleModelId|suggestionModelId|translateModeId|ocrModelId|compressModelId'
```

Confirm Chat, Title, Translation, and OCR remain editable. Keep Fast,
Suggestion, and Compression behavior either represented explicitly in the
single section or preserved through an additional named compatibility row; do
not silently remove them during extraction.

- [ ] **Step 2: Run focused registry and UI tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*modelregistry*' --tests '*models*' --no-daemon
```

Expected: all matching tests PASS.

- [ ] **Step 3: Run complete verification**

```bash
./gradlew test assembleDebug --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
git diff --check
```

Expected: Gradle exits 0 for both commands and `git diff --check` produces no
output.

- [ ] **Step 4: Review the final diff**

```bash
git status --short
git diff --stat HEAD~4..HEAD
git diff HEAD~4..HEAD -- app/src/main/java/me/rerere/rikkahub/RouteActivity.kt app/src/main/java/me/rerere/rikkahub/ui/pages/models
```

Confirm the final diff has one Models route, no second assignment destination,
no provider/storage rewrites, and no accidental changes to unrelated worktree
files.

- [ ] **Step 5: Commit any verification-only fixes**

```bash
git add <only-fixed-pr6-files>
git commit -m "fix: preserve unified model assignment parity"
```

Use the exact affected paths in place of `<only-fixed-pr6-files>`; never use
`git add .`.
