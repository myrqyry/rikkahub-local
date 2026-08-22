# Models Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace three Settings AI destinations (Model Manager, Default Models, Providers) with one top-level **Models** page where the model is the primary object, local/cloud is a source badge, assignments are folded into a "Used by default" section, and provider management lives in a full-height Manage Sources sheet.

**Architecture:** UI/product consolidation only — no data-layer rewrite. The registry (`ModelRegistry`, `SettingsModelRegistry`), `UnifiedModelsViewModel`, `ModelAssignmentsSection`, `SettingProviderDetailPage`, and `ModelManagerViewModel` are preserved. New pure taxonomy (`ModelsFilter`), new `ModelsPage`/`ModelDetailPage` composables, and two sheets (`AddToModelsSheet`, `ManageSourcesSheet`) extracted from deleted pages.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation3 (`Screen` sealed class + `entry<Screen.X>` in `RouteActivity`), Koin (typed `koinViewModel<T>()`), ObjectBox-backed `AppSettings`, Material 3 + HugeIcons, `sh.calvin.reorderable`.

## Global Constraints

- Spec is authoritative: `docs/superpowers/specs/2026-08-17-models-consolidation-design.md` (commit 71452ecd).
- **Source rows say Configured / Disabled / Not configured — NEVER "Connected".** `ModelProviderDescriptor` exposes only `id`, `displayName`, `enabled`, `modelIds`. Per-descriptor `connected` is used only on Model Detail.
- **Compatibility aliases MUST stay:** `Screen.SettingModelManager`, `Screen.SettingDefaultModels`, `Screen.SettingProvider` remain as deprecated route types rendering `ModelsPage` with anchors. Delete them only after restore-compat is proven (out of scope for this plan).
- `ModelManagerViewModel` is registered parameterised in Koin: MUST use typed `koinViewModel<ModelManagerViewModel>()` (the type-less overload crashes).
- Preserve installed app data. Never uninstall / `pm clear` / change `applicationId` / reset databases. Install dev APKs with `adb install -r`.
- Do not remove Chinese/custom providers, provider network behavior, persisted default-model fields, or the assignment machinery.
- Existing tests `UnifiedModelsViewModelTest` and `ModelAssignmentsSectionTest` MUST stay green.
- Every file created/edited here keeps existing package structure and HugeIcons/Material3 conventions.
- NO code comments unless they document a deliberate simplification (mark with `ponytail:`).
- **Verification gate (every task compiles):** `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`. Full gate at end: same + `./gradlew lint`.

---

### Task 1: Pure taxonomy — `ModelsFilter.kt` + tests

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsFilter.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelsFilterTest.kt`

**Interfaces:**
- Consumes: `ModelDescriptor`, `ModelCapability`, `ModelSource`, `ModelTab` (from `data/modelregistry/ModelRegistryModels.kt` and `ui/pages/models/ModelsPageRequest.kt` — both exist, unchanged).
- Produces: `enum class ModelsFilter { ALL, CHAT, VISION, IMAGE, AUDIO, RETRIEVAL }`, `fun ModelsFilter.matches(model: ModelDescriptor): Boolean`, `fun ModelTab.toModelsFilter(): ModelsFilter`, `fun searchMatches(model: ModelDescriptor, query: String): Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun descriptor(
    id: String,
    displayName: String,
    capabilities: Set<ModelCapability>,
    source: ModelSource = ModelSource.Local(me.rerere.rikkahub.data.localruntime.LocalRuntime.LiteRT),
) = ModelDescriptor(
    id = id,
    displayName = displayName,
    source = source,
    capabilities = capabilities,
    enabledCapabilities = capabilities,
    lifecycle = ModelLifecycle.READY,
)

class ModelsFilterTest {
    @Test
    fun `ALL matches everything`() {
        assertTrue(ModelsFilter.ALL.matches(descriptor("a", "x", setOf(ModelCapability.CHAT))))
    }

    @Test
    fun `CHAT matches only chat`() {
        assertTrue(ModelsFilter.CHAT.matches(descriptor("a", "x", setOf(ModelCapability.CHAT))))
        assertFalse(ModelsFilter.CHAT.matches(descriptor("b", "y", setOf(ModelCapability.VISION))))
    }

    @Test
    fun `VISION covers OCR and document analysis`() {
        setOf(
            ModelCapability.VISION,
            ModelCapability.OCR,
            ModelCapability.DOCUMENT_ANALYSIS,
        ).forEach { cap ->
            assertTrue(ModelsFilter.VISION.matches(descriptor("a", "x", setOf(cap))))
        }
        assertFalse(ModelsFilter.VISION.matches(descriptor("b", "y", setOf(ModelCapability.CHAT))))
    }

    @Test
    fun `IMAGE covers generation and editing`() {
        assertTrue(ModelsFilter.IMAGE.matches(descriptor("a", "x", setOf(ModelCapability.IMAGE_GENERATION))))
        assertTrue(ModelsFilter.IMAGE.matches(descriptor("b", "y", setOf(ModelCapability.IMAGE_EDITING))))
        assertFalse(ModelsFilter.IMAGE.matches(descriptor("c", "z", setOf(ModelCapability.CHAT))))
    }

    @Test
    fun `AUDIO covers tts stt and understanding`() {
        setOf(
            ModelCapability.TEXT_TO_SPEECH,
            ModelCapability.SPEECH_TO_TEXT,
            ModelCapability.AUDIO_UNDERSTANDING,
        ).forEach { cap ->
            assertTrue(ModelsFilter.AUDIO.matches(descriptor("a", "x", setOf(cap))))
        }
    }

    @Test
    fun `RETRIEVAL covers embeddings and reranking`() {
        assertTrue(ModelsFilter.RETRIEVAL.matches(descriptor("a", "x", setOf(ModelCapability.EMBEDDINGS))))
        assertTrue(ModelsFilter.RETRIEVAL.matches(descriptor("b", "y", setOf(ModelCapability.RERANKING))))
    }

    @Test
    fun `tab maps to nearest filter`() {
        assertTrue(ModelTab.ALL.toModelsFilter() == ModelsFilter.ALL)
        assertTrue(ModelTab.CHAT.toModelsFilter() == ModelsFilter.CHAT)
        assertTrue(ModelTab.VISION.toModelsFilter() == ModelsFilter.VISION)
        assertTrue(ModelTab.IMAGE.toModelsFilter() == ModelsFilter.IMAGE)
        assertTrue(ModelTab.SPEECH.toModelsFilter() == ModelsFilter.AUDIO)
        assertTrue(ModelTab.EMBEDDINGS.toModelsFilter() == ModelsFilter.RETRIEVAL)
        assertTrue(ModelTab.TASK.toModelsFilter() == ModelsFilter.VISION)
        assertTrue(ModelTab.OTHER.toModelsFilter() == ModelsFilter.ALL)
    }

    @Test
    fun `search matches display name and id case-insensitively`() {
        val model = descriptor("local:litert:gemma", "Gemma 4 E2B", setOf(ModelCapability.CHAT))
        assertTrue(searchMatches(model, "gemma"))
        assertTrue(searchMatches(model, "GEMMA"))
        assertTrue(searchMatches(model, "litert"))
        assertFalse(searchMatches(model, "mistral"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.ModelsFilterTest"`
Expected: FAIL — `ModelsFilter` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor

enum class ModelsFilter {
    ALL, CHAT, VISION, IMAGE, AUDIO, RETRIEVAL;

    fun matches(model: ModelDescriptor): Boolean = when (this) {
        ALL -> true
        CHAT -> model.supports(ModelCapability.CHAT)
        VISION -> model.capabilities.any {
            it == ModelCapability.VISION ||
                it == ModelCapability.OCR ||
                it == ModelCapability.DOCUMENT_ANALYSIS
        }
        IMAGE -> model.capabilities.any {
            it == ModelCapability.IMAGE_GENERATION || it == ModelCapability.IMAGE_EDITING
        }
        AUDIO -> model.capabilities.any {
            it == ModelCapability.TEXT_TO_SPEECH ||
                it == ModelCapability.SPEECH_TO_TEXT ||
                it == ModelCapability.AUDIO_UNDERSTANDING
        }
        RETRIEVAL -> model.capabilities.any {
            it == ModelCapability.EMBEDDINGS || it == ModelCapability.RERANKING
        }
    }
}

fun ModelTab.toModelsFilter(): ModelsFilter = when (this) {
    ModelTab.ALL -> ModelsFilter.ALL
    ModelTab.CHAT -> ModelsFilter.CHAT
    ModelTab.VISION -> ModelsFilter.VISION
    ModelTab.IMAGE -> ModelsFilter.IMAGE
    ModelTab.SPEECH -> ModelsFilter.AUDIO
    ModelTab.EMBEDDINGS -> ModelsFilter.RETRIEVAL
    ModelTab.TASK -> ModelsFilter.VISION
    ModelTab.OTHER -> ModelsFilter.ALL
}

fun searchMatches(model: ModelDescriptor, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return model.displayName.lowercase().contains(q) || model.id.lowercase().contains(q)
}
```

`ModelTab` and `ModelManagerRequest` live in `ui/pages/models/ModelsPageRequest.kt` (unchanged). `supports` already exists on `ModelDescriptor`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.ModelsFilterTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsFilter.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelsFilterTest.kt
git commit -m "feat: add capability-based ModelsFilter taxonomy"
```

---

### Task 2: Pure summary — `DefaultAssignmentsSummary.kt` + tests

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/DefaultAssignmentsSummary.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/DefaultAssignmentsSummaryTest.kt`

**Interfaces:**
- Consumes: `ModelAssignments`, `ModelRole`, `ModelDescriptor`, `ModelSource` (all from `ModelRegistryModels.kt`).
- Produces: `data class AssignmentSummaryRow(role: ModelRole, model: ModelDescriptor?)`, `fun defaultAssignmentsSummary(assignments: ModelAssignments, models: List<ModelDescriptor>): List<AssignmentSummaryRow>` (only rows with a resolvable model; model resolved by id match against `models`; legacy keys ignored).

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.localruntime.LocalRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAssignmentsSummaryTest {
    private fun local(id: String, name: String) = ModelDescriptor(
        id = id,
        displayName = name,
        source = ModelSource.Local(LocalRuntime.LiteRT),
        capabilities = setOf(ModelCapability.CHAT),
        enabledCapabilities = setOf(ModelCapability.CHAT),
        lifecycle = ModelLifecycle.READY,
    )

    @Test
    fun `returns one row per assigned role with resolved model`() {
        val gemma = local("gemma", "Gemma 4")
        val assignments = ModelAssignments(
            defaults = mapOf(
                ModelRole.CHAT to "gemma",
                ModelRole.VISION to "missing-model",
            ),
        )
        val rows = defaultAssignmentsSummary(assignments, listOf(gemma))
        assertEquals(1, rows.size)
        assertEquals(ModelRole.CHAT, rows[0].role)
        assertEquals(gemma, rows[0].model)
    }

    @Test
    fun `skips rows whose model cannot be resolved`() {
        val assignments = ModelAssignments(
            defaults = mapOf(ModelRole.OCR to "ghost"),
        )
        assertTrue(defaultAssignmentsSummary(assignments, emptyList()).isEmpty())
    }

    @Test
    fun `no assignment defaults produce empty list`() {
        assertTrue(defaultAssignmentsSummary(ModelAssignments(), emptyList()).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.DefaultAssignmentsSummaryTest"`
Expected: FAIL — `defaultAssignmentsSummary` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRole

data class AssignmentSummaryRow(
    val role: ModelRole,
    val model: ModelDescriptor?,
)

fun defaultAssignmentsSummary(
    assignments: ModelAssignments,
    models: List<ModelDescriptor>,
): List<AssignmentSummaryRow> {
    val byId = models.associateBy { it.id }
    return ModelRole.entries.mapNotNull { role ->
        val modelId = assignments.defaults[role] ?: return@mapNotNull null
        AssignmentSummaryRow(role, byId[modelId])
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.DefaultAssignmentsSummaryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/DefaultAssignmentsSummary.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models/DefaultAssignmentsSummaryTest.kt
git commit -m "feat: add pure default assignments summary"
```

---

### Task 3: Source badge + status labels — `SourceBadge.kt` + tests

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/SourceBadge.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/components/SourceBadgeTest.kt`

**Interfaces:**
- Consumes: `ModelDescriptor`, `ModelSource` (from `ModelRegistryModels.kt`).
- Produces: pure helpers (Composable badge is UI-only, but label+status strings are pure): `fun sourceLabel(model: ModelDescriptor): SourceLabel` where `sealed interface SourceLabel { data class Local; data class Provider(name: String) }`, `fun sourceDisplayName(model: ModelDescriptor): String` (Local → `"On device"`, Cloud → `metadata["provider"] ?: providerId`), `fun statusLabel(model: ModelDescriptor): String` (returns a translatable string resource name — see Step 3 for design).

**Design note:** the Composable `SourceBadge(model: ModelDescriptor)` renders a small rounded `Surface` chip with `sourceDisplayName`. The pure part (testable) is the label derivation. Status labels are driven by string resources at render time; the pure helper returns an enum so the composable can pick the resource.

- [ ] **Step 1: Write the failing test**

```kotlin
package me.rerere.rikkahub.ui.pages.models.components

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.localruntime.LocalRuntime
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceBadgeTest {
    private fun model(source: ModelSource, metadata: Map<String, String> = emptyMap()) =
        ModelDescriptor(
            id = "m",
            displayName = "M",
            source = source,
            capabilities = setOf(ModelCapability.CHAT),
            enabledCapabilities = setOf(ModelCapability.CHAT),
            lifecycle = ModelLifecycle.READY,
            metadata = metadata,
        )

    @Test
    fun `local source label is On device`() {
        assertEquals("On device", sourceDisplayName(model(ModelSource.Local(LocalRuntime.LiteRT))))
    }

    @Test
    fun `cloud source uses provider metadata name`() {
        assertEquals(
            "OpenAI",
            sourceDisplayName(
                model(
                    ModelSource.Cloud("uuid", "gpt"),
                    metadata = mapOf("provider" to "OpenAI"),
                ),
            ),
        )
    }

    @Test
    fun `cloud source falls back to provider id`() {
        assertEquals("uuid", sourceDisplayName(model(ModelSource.Cloud("uuid", "gpt"))))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.components.SourceBadgeTest"`
Expected: FAIL — `sourceDisplayName` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource

// ponytail: label/status helpers are pure strings derived at render time;
// keep them as functions so UI stays dumb and tests stay trivial.

fun sourceDisplayName(model: ModelDescriptor): String = when (val source = model.source) {
    is ModelSource.Local -> "On device"
    is ModelSource.Cloud -> model.metadata["provider"] ?: source.providerId
}

@Composable
fun SourceBadge(
    model: ModelDescriptor,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = sourceDisplayName(model),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.components.SourceBadgeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/SourceBadge.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models/components/SourceBadgeTest.kt
git commit -m "feat: add source badge and display-name helper"
```

---

### Task 4: Registry metadata — `sizeBytes`/`path` for local models

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/SettingsModelRegistry.kt` (in `descriptor()`, ~lines 201-237)

**Interfaces:**
- Consumes: existing `descriptor(provider, model, localFiles, disabledCapabilities)` internals (`Model`, `Map<LocalRuntime, Map<String,String>>` localFiles).
- Produces: local models' `ModelDescriptor.metadata` now includes `"path"` (file path) and `"sizeBytes"` (file length as string). `ModelDetailPage` (Task 8) reads `metadata["sizeBytes"]` for Storage and `metadata["path"]` for Location.

- [ ] **Step 1: Read the current `descriptor()` body**

Run: `sed -n '195,240p' app/src/main/java/me/rerere/rikkahub/data/modelregistry/SettingsModelRegistry.kt`
Confirm: local branch currently builds `metadata = mapOf("provider" to provider.name)` with no path/size.

- [ ] **Step 2: Modify the local-model metadata construction**

In `SettingsModelRegistry.kt`, inside the `ModelDescriptor(...)` construction in `descriptor()`, replace the `metadata = ...` argument so local models carry path + size. The local file path for provider-backed locals is `localFiles[runtime]?.get(model.modelId)`. Change:

```kotlin
metadata = mapOf("provider" to provider.name),
```

to:

```kotlin
metadata = buildMap {
    put("provider", provider.name)
    val path = localFiles[runtime]?.get(model.modelId)
    if (path != null) {
        put("path", path)
        put("sizeBytes", File(path).length().toString())
    }
},
```

Add import `java.io.File` if not already present. `localFiles[runtime]` is a `Map<String, String>` keyed by `model.modelId` mapping to file path (verify exact shape at Step 1 — the descriptor already computes `files = localFiles[runtime]?.keys.filter { it == model.modelId }`, so the map value is the path).

- [ ] **Step 3: Compile + run existing registry-adjacent tests**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.*" :app:compileDebugKotlin`
Expected: PASS; `UnifiedModelsViewModelTest` and `ModelAssignmentsSectionTest` green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/modelregistry/SettingsModelRegistry.kt
git commit -m "feat: add path and sizeBytes metadata to local model descriptors"
```

---

### Task 5: VM thin method — `setCapabilityEnabled`

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModel.kt`

**Interfaces:**
- Consumes: existing `setModelEnabled(model, enabled)` pattern and `registry.setCapabilityEnabled(modelId, capability, enabled)`.
- Produces: `fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean)` — same launch/try/catch/`_operationError` semantics as `setModelEnabled`, single-capability. `ModelDetailPage` (Task 8) uses it.

- [ ] **Step 1: Add the method**

Mirror the existing `setModelEnabled` body. Find its implementation (it launches in `ownerScope`, rethrows `CancellationException`, catches generic → `_operationError`) and add directly below it:

```kotlin
fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) {
    ownerScope.launch {
        try {
            registry.setCapabilityEnabled(modelId, capability, enabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _operationError.value = e
        }
    }
}
```

(If `setModelEnabled` uses a different coroutine context / error-field name, mirror it exactly — verify at implementation time by reading the method.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/UnifiedModelsViewModel.kt
git commit -m "feat: expose per-capability enable toggle on UnifiedModelsViewModel"
```

---

### Task 6: Flatten `ModelInventorySection`

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt`

**Interfaces:**
- Consumes: `ModelDescriptor`, `ModelsFilter`, `searchMatches`, `sourceDisplayName`, `SourceBadge`.
- Produces: NEW signature — `ModelInventorySection(models: List<ModelDescriptor>, filter: ModelsFilter, searchQuery: String, onModelEnabledChange: (ModelDescriptor, Boolean) -> Unit, onModelClick: (ModelDescriptor) -> Unit = {})`. Uniform cards, no local/cloud split, no provider rows, no rename/delete (moved to Model Detail), no provider configure/refresh. Each card: `CardGroup` `item(onClick = { onModelClick(model) })` with `headlineContent = displayName`, `leadingContent = SourceBadge(model)` (optional if CardGroup lacks leading slot — if so, put badge in supporting line), `supportingContent = Text(model.capabilities.joinToString(" • ") { it.name.lowercase() })`, `trailingContent = Switch(checked = model.enabledCapabilities.isNotEmpty(), onCheckedChange = { onModelEnabledChange(model, it) })`. Filtering applied by caller (ModelsPage) OR here — caller applies (page owns `filter`/`searchQuery`, passes already-filtered `models`; the two params remain for the empty-state string `unified_models_empty`).

**Decision (ponytail):** caller pre-filters; keep the composable dumb. Parameters `filter`/`searchQuery` are NOT needed if caller pre-filters — drop them, signature becomes `ModelInventorySection(models, onModelEnabledChange, onModelClick, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Rewrite the file**

Replace the entire content of `ModelInventorySection.kt` with:

```kotlin
package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
fun ModelInventorySection(
    models: List<ModelDescriptor>,
    onModelEnabledChange: (ModelDescriptor, Boolean) -> Unit,
    onModelClick: (ModelDescriptor) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (models.isEmpty()) {
            Text(
                stringResource(R.string.unified_models_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CardGroup {
                models.forEach { model ->
                    item(
                        onClick = { onModelClick(model) },
                        headlineContent = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(model.displayName)
                                SourceBadge(
                                    model = model,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        },
                        supportingContent = { Text(model.capabilities.joinToString(" • ") { it.name.lowercase() }) },
                        trailingContent = {
                            Switch(
                                checked = model.enabledCapabilities.isNotEmpty(),
                                onCheckedChange = { onModelEnabledChange(model, it) },
                            )
                        },
                    )
                }
            }
        }
    }
}
```

Confirm `CardGroup`'s `item` supports the params used (headlineContent/supportingContent/trailingContent/onClick — these exist per the old file's usage). If `CardGroup` requires a `title`, pass none or `title = {}`.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL ONLY IF other files still call the old 9-arg signature — that is EXPECTED mid-plan. If it fails solely on call-site mismatches, note them and proceed (they're fixed in Task 9/10 which replace callers). If it fails on the new file itself, fix and re-run.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt
git commit -m "refactor: flatten model inventory to uniform cards"
```

---

### Task 7: `AddToModelsSheet.kt`

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/AddToModelsSheet.kt`

**Interfaces:**
- Consumes: `ModelManagerViewModel` (typed `koinViewModel<ModelManagerViewModel>()`), `RECOMMENDED_PROVIDERS` (from `data/datastore`), `ProviderConfigure` (from `ui/pages/setting/components`), `useEditState` (from `ui/hooks`), `AppSettings`/`SettingVM`.
- Produces: `@Composable fun AddToModelsSheet(onDismiss: () -> Unit, onSourceAdded: () -> Unit = {})` — a `ModalBottomSheet` with two groups. The **On device** group is the `AddModelOptions` composable (import from file / HF URL / catalog) extracted verbatim from `ModelManagerPage.kt`. The **Connect a source** group lists `RECOMMENDED_PROVIDERS` + a custom-endpoint `ProviderConfigure` dialog.

- [ ] **Step 1: Read the source to extract**

Run: `sed -n '1,453p' app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerPage.kt`
Identify the `AddModelOptions`, `HfUrlTab`, `LocalImportTab`, `SdCatalogEntryCard`, `AddModelSectionHeader` composables and their imports. These move into `AddToModelsSheet.kt` (private), unchanged except `AddModelOptions` gains a wrapping in the sheet's scroll column.

- [ ] **Step 2: Create the sheet**

Write `AddToModelsSheet.kt`:

```kotlin
package me.rerere.rikkahub.ui.pages.models.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.RECOMMENDED_PROVIDERS
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.modelmanager.ModelManagerViewModel
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.androidx.compose.koinViewModel
import me.rerere.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToModelsSheet(
    viewModel: ModelManagerViewModel = koinViewModel<ModelManagerViewModel>(),
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val toaster = LocalToaster.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importModelFromUri(it) } }
    var showAddProvider by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // "On device" group — the extracted ModelManager add UI.
        AddModelSectionHeader(
            title = stringResource(R.string.models_add_on_device),
            subtitle = stringResource(R.string.models_add_on_device_subtitle),
        )
        // Reuse the extracted AddModelOptions exactly (catalog/HF-url/file), including
        // its internal LazyColumn content and bottom progress/error display.
        AddModelOptions(
            viewModel = viewModel,
            filePickerLauncher = filePickerLauncher,
            downloadProgress = viewModel.downloadProgress,
            errorMessage = viewModel.errorMessage,
        )

        // "Connect a source" group.
        AddModelSectionHeader(
            title = stringResource(R.string.models_add_connect_source),
            subtitle = stringResource(R.string.models_add_connect_source_subtitle),
        )
        RECOMMENDED_PROVIDERS.forEach { provider ->
            TextButton(
                onClick = {
                    showAddProvider = true
                },
            ) {
                Text(provider.name)
            }
        }
    }

    if (showAddProvider) {
        val editState = useEditState(ProviderSetting.newEmpty())
        val providerSetting = editState.value
        AlertDialog(
            onDismissRequest = { showAddProvider = false },
            title = { Text(stringResource(R.string.setting_provider_page_add_provider)) },
            text = {
                ProviderConfigure(value = providerSetting) { editState.update(it) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Write the new provider into settings (mirror SettingProviderPage's AddButton).
                        viewModel.addProvider(providerSetting)
                        showAddProvider = false
                    },
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProvider = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
```

**IMPORTANT:** `viewModel.addProvider(...)` does NOT exist on `ModelManagerViewModel`. Provider creation writes to `SettingVM.updateSettings(settings.copy(providers = ...))`. Two options:
  (a) Add a thin `addProvider` to `ModelManagerViewModel` that obtains `SettingVM` (or the settings store) — acceptable, mirroring how `ModelManagerViewModel` already holds a settings reference.
  (b) Pass `SettingVM` into the sheet and do the write here.
Pick (a) at implementation time by reading `ModelManagerViewModel`'s constructor; add:

```kotlin
fun addProvider(provider: ProviderSetting) {
    // writes settings.copy(providers = providers + provider) via the same store
    // SettingProviderPage's AddButton uses
}
```

and keep the UI above unchanged. The `AddModelOptions`/`HfUrlTab`/`LocalImportTab`/`SdCatalogEntryCard`/`AddModelSectionHeader` composables and their exact imports come from `ModelManagerPage.kt` (Step 1). Keep them byte-identical, only moving the `+ Add model / Back to models` scaffold (bottom bar) out.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS (or only the expected mid-plan call-site breakage from Task 6; note and continue).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/AddToModelsSheet.kt
git commit -m "feat: add AddToModelsSheet combining on-device add and connect-a-source"
```

---

### Task 8: `ManageSourcesSheet.kt`

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ManageSourcesSheet.kt`

**Interfaces:**
- Consumes: `SettingVM` (`koinViewModel<SettingVM>()`), `AppSettings`, `ProviderSetting` (`me.rerere.ai.provider.ProviderSetting`), `AutoAIIcon`/`Tag`/`TagType` (`ui.components.ui`), `sh.calvin.reorderable.*`, `LocalToaster`, `LocalNavController` (`ui.context`), `Screen.SettingProviderDetail` (`root`).
- Produces: `@Composable fun ManageSourcesSheet(onDismiss: () -> Unit)` — full-height `ModalBottomSheet` containing the provider list-management UI extracted from `SettingProviderPage.kt`: search field, `ReorderableItem` list, `ProviderItem` rows (drag handle, long-press delete, click → `SettingProviderDetail`), delete dialog. Recommend/Import/Add buttons from `SettingProviderPage`'s top bar are NOT copied (the `＋` sheet's Connect-a-source covers adding); if keeping them is trivial, they may be included as trailing actions in the sheet header.

- [ ] **Step 1: Read the source to extract**

Run: `sed -n '1,707p' app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`
Copy the `ProviderItem`, reorderable `LazyColumn`, search field, delete dialog, and their supporting composables + imports into the sheet (full-height `ModalBottomSheet`, `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`).

- [ ] **Step 2: Create the sheet**

Write `ManageSourcesSheet.kt`. Structure:

```kotlin
package me.rerere.rikkahub.ui.pages.models.components

// imports: as found in SettingProviderPage.kt Step 1 (Material3, reorderable,
// HugeIcons, AutoAIIcon/Tag/TagType, ProviderSetting, Screen, useEditState not needed)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSourcesSheet(
    vm: SettingVM = koinViewModel<SettingVM>(),
    onDismiss: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navController = LocalNavController.current
    var searchQuery by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ProviderSetting?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Search field (SettingProviderPage's filter), LazyColumn of ReorderableItem(ProviderItem),
        // delete AlertDialog — all copied from SettingProviderPage with onClick → Screen.SettingProviderDetail(provider.id.toString())
        // and the same drag-reorder write to vm.updateSettings(settings.copy(providers = ...)).
    }
}
```

This is a direct extraction; the exact code is in `SettingProviderPage.kt` at Step 1. Keep behavior identical: reorder persists, delete persists (`deletedBuiltInProviderIds` for built-in providers), search filters.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS (or only expected mid-plan call-site breakage).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ManageSourcesSheet.kt
git commit -m "feat: extract provider list management into ManageSourcesSheet"
```

---

### Task 9: `ModelsPage.kt`

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPage.kt`

**Interfaces:**
- Consumes: `UnifiedModelsViewModel` (koin), `SettingVM` (koin), `ModelManagerViewModel` (typed koin, for the sheet), `ModelsFilter`/`searchMatches`/`toModelsFilter` (Task 1), `defaultAssignmentsSummary` (Task 2), `SourceBadge` (Task 3), `ModelInventorySection` (Task 6), `AddToModelsSheet` (Task 7), `ManageSourcesSheet` (Task 8), `ModelAssignmentsSection` (existing), `Screen` routes (`root`), `LocalNavController`, `ProviderSetting`/`AppSettings`.
- Produces: `@Composable fun ModelsPage(request: ModelManagerRequest = ModelManagerRequest(), showAssignments: Boolean = false, scrollToSources: Boolean = false)` — the single page; reads `managerVisibleModels` (shows disabled too), `registryProviders`, `assignments`, `legacyAssignments`.

- [ ] **Step 1: Write the page scaffold + state**

```kotlin
package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.root.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.AddToModelsSheet
import me.rerere.rikkahub.ui.pages.models.components.ManageSourcesSheet
import me.rerere.rikkahub.ui.pages.models.components.ModelInventorySection
import me.rerere.rikkahub.ui.pages.models.components.SourceBadge
import me.rerere.rikkahub.ui.pages.models.components.ModelAssignmentsSection
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsPage(
    request: ModelManagerRequest = ModelManagerRequest(),
    showAssignments: Boolean = false,
    scrollToSources: Boolean = false,
    vm: UnifiedModelsViewModel = koinViewModel(),
    settingsVm: SettingVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val managerVisibleModels by vm.managerVisibleModels.collectAsState()
    val providers by vm.registryProviders.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val legacyAssignments by vm.legacyAssignments.collectAsState()
    val settings by settingsVm.settings.collectAsState()

    var query by rememberSaveable { mutableStateOf(request.search) }
    var filter by rememberSaveable { mutableStateOf(request.tab.toModelsFilter()) }
    var expandedAssignments by rememberSaveable { mutableStateOf(showAssignments) }
    var localOnly by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showManageSources by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(request) {
        filter = request.tab.toModelsFilter()
        query = request.search
    }

    val visible = managerVisibleModels
        .filter { filter.matches(it) || filter == ModelsFilter.ALL }
        .filter { searchMatches(it, query) }
        .filter { !localOnly || it.source is ModelSource.Local }

    val summaryRows = defaultAssignmentsSummary(assignments, managerVisibleModels)
    val searching = query.isNotBlank()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.models_add))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 1) Search + chips
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.unified_models_search)) },
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(HugeIcons.Cancel01, stringResource(R.string.clear))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = CircleShape,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelsFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(f.labelRes())) },
                        )
                    }
                }
            }

            // 2) Used by default (hidden while searching)
            if (!searching) {
                item {
                    Text(stringResource(R.string.models_used_by_default), style = MaterialTheme.typography.titleSmall)
                }
                if (summaryRows.isNotEmpty()) {
                    item {
                        CardGroup {
                            summaryRows.forEach { row ->
                                val model = row.model ?: return@forEach
                                item(
                                    onClick = { navController.navigate(Screen.ModelDetail(model.id)) },
                                    headlineContent = { Text(row.role.labelRes().let { stringResource(it) }) },
                                    supportingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(model.displayName)
                                            SourceBadge(model = model, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { expandedAssignments = !expandedAssignments }) {
                        Text(
                            stringResource(
                                if (expandedAssignments) R.string.models_hide_assignments
                                else R.string.models_show_all_assignments,
                            ),
                        )
                    }
                }
                if (expandedAssignments) {
                    item {
                        ModelAssignmentsSection(
                            assignments = assignments,
                            legacyAssignments = legacyAssignments,
                            models = managerVisibleModels,
                            repairState = null,
                            onAssign = { role, id -> vm.assign(role, id) },
                            onAssignTitle = { vm.assignTitle(it) },
                            onAssignTranslation = { vm.assignTranslation(it) },
                            fastModelId = settings.fastModelId?.toString(),
                            suggestionModelId = settings.suggestionModelId?.toString(),
                            compressModelId = settings.compressModelId?.toString(),
                            enableSuggestion = settings.enableSuggestion,
                            onSuggestionEnabledChange = { settingsVm.updateSettings(settings.copy(enableSuggestion = it)) },
                            onFastModelSelected = { id -> settingsVm.updateSettings(settings.copy(fastModelId = Uuid.parse(id))) },
                            onSuggestionModelSelected = { id -> settingsVm.updateSettings(settings.copy(suggestionModelId = Uuid.parse(id))) },
                            onCompressModelSelected = { id -> settingsVm.updateSettings(settings.copy(compressModelId = Uuid.parse(id))) },
                        )
                    }
                }
            }

            // 3) Your models
            item {
                Text(stringResource(R.string.models_your_models), style = MaterialTheme.typography.titleSmall)
            }
            item {
                ModelInventorySection(
                    models = visible,
                    onModelEnabledChange = { model, enabled -> vm.setModelEnabled(model, enabled) },
                    onModelClick = { model -> navController.navigate(Screen.ModelDetail(model.id)) },
                )
            }

            // 4) Sources (hidden while searching)
            if (!searching) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.models_sources), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { showManageSources = true }) {
                            Text(stringResource(R.string.models_manage_sources))
                        }
                    }
                }
                val localCount = managerVisibleModels.count { it.source is ModelSource.Local }
                item {
                    CardGroup {
                        item(
                            onClick = { localOnly = !localOnly },
                            headlineContent = { Text(stringResource(R.string.models_on_this_device)) },
                            supportingContent = { Text(stringResource(R.string.unified_models_provider_count, localCount)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        providers.forEach { provider ->
                            item(
                                onClick = { navController.navigate(Screen.SettingProviderDetail(provider.id)) },
                                headlineContent = { Text(provider.displayName) },
                                supportingContent = {
                                    Text(
                                        when {
                                            !provider.enabled -> stringResource(R.string.models_source_disabled)
                                            provider.modelIds.isEmpty() -> stringResource(R.string.models_source_not_configured)
                                            else -> stringResource(
                                                R.string.models_source_configured_count,
                                                provider.modelIds.size,
                                            )
                                        },
                                    )
                                },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddToModelsSheet(onDismiss = { showAddSheet = false })
    }
    if (showManageSources) {
        ManageSourcesSheet(onDismiss = { showManageSources = false })
    }
}

private fun ModelsFilter.labelRes(): Int = when (this) {
    ModelsFilter.ALL -> R.string.models_filter_all
    ModelsFilter.CHAT -> R.string.models_filter_chat
    ModelsFilter.VISION -> R.string.models_filter_vision
    ModelsFilter.IMAGE -> R.string.models_filter_image
    ModelsFilter.AUDIO -> R.string.models_filter_audio
    ModelsFilter.RETRIEVAL -> R.string.models_filter_retrieval
}

private fun ModelRole.labelRes(): Int = when (this) {
    ModelRole.CHAT -> R.string.models_role_chat
    ModelRole.VISION -> R.string.models_role_vision
    ModelRole.OCR -> R.string.models_role_ocr
    ModelRole.IMAGE_GENERATION -> R.string.models_role_image_generation
    ModelRole.IMAGE_EDITING -> R.string.models_role_image_editing
    ModelRole.TEXT_TO_SPEECH -> R.string.models_role_text_to_speech
    ModelRole.SPEECH_TO_TEXT -> R.string.models_role_speech_to_text
    ModelRole.EMBEDDINGS -> R.string.models_role_embeddings
}
```

**Notes for implementer:**
- Verify `ModelAssignmentsSection`'s exact parameter names against `DefaultModelsPage.kt` (Task source read) and pass through exactly — the block above assumes the `settings` fields are nullable `Uuid?`. If `fastModelId`/`compressModelId` are non-null `Uuid`, drop the `?.`. If they are `String`, drop `Uuid.parse`. Read `DefaultModelsPage.kt` at implementation time and mirror its exact wiring — it is the canonical template.
- `scrollToSources: Boolean` → on first composition, if true, `LaunchedEffect(Unit) { listState.animateScrollToItem(<sources index>) }` — simplest correct approach: scroll to the "Sources" header. Since indexes are fragile, acceptable ponytail: scroll to a fixed large index or to the last item. Implement `if (scrollToSources) listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)` in a `LaunchedEffect(scrollToSources)`.
- `repairState`/`operationError` wiring from ModelManagerPage may be added if cheap; spec treats them as preserved, not required on the new page. Keep `operationError` surfacing if trivial (snackbar), else skip (ponytail).
- Add missing imports (`CircleShape`, `PaddingValues`, `ModelRole`, `Uuid`) — the compiler will direct.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL on the new `R.string.*` keys (added in Task 11) and on any `ModelAssignmentsSection` signature drift. Fix signature drift now; note the R.string gaps (Task 11 adds them). If failure is ONLY missing strings, that is acceptable mid-plan — proceed; but if you can, add the strings now by reading Task 11's key list.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPage.kt
git commit -m "feat: add consolidated Models page"
```

---

### Task 10: `ModelDetailPage.kt`

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelDetailPage.kt`

**Interfaces:**
- Consumes: `UnifiedModelsViewModel` (koin, incl. new `setCapabilityEnabled`), `SettingVM` (koin), `SourceBadge` (Task 3), `defaultAssignmentsSummary` (Task 2), `Screen`/`LocalNavController`, `Uuid` parsing.
- Produces: `@Composable fun ModelDetailPage(modelId: String, vm: UnifiedModelsViewModel = koinViewModel(), settingsVm: SettingVM = koinViewModel())`.

- [ ] **Step 1: Write the page**

```kotlin
package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.root.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.models.components.SourceBadge
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailPage(
    modelId: String,
    vm: UnifiedModelsViewModel = koinViewModel(),
    settingsVm: SettingVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val allModels by vm.allModels.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val model = allModels.firstOrNull { it.id == modelId }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf(false) }

    if (model == null) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.models_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                        }
                    },
                )
            },
        ) {
            Text(
                stringResource(R.string.unified_models_empty),
                modifier = Modifier.padding(it),
            )
        }
        return
    }

    val usedFor = defaultAssignmentsSummary(assignments, allModels)
        .filter { it.model?.id == model.id }
        .map { it.role }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(model.displayName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (model.source is ModelSource.Local) {
                        IconButton(onClick = {
                            renaming = true
                            renameText = model.displayName
                        }) {
                            Icon(HugeIcons.Edit01, stringResource(R.string.local_llm_rename))
                        }
                        IconButton(onClick = { pendingDelete = true }) {
                            Icon(HugeIcons.Delete01, stringResource(R.string.local_llm_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Source
            item {
                CardGroup(title = { Text(stringResource(R.string.models_source_section)) }) {
                    item(
                        headlineContent = { Text(sourceDisplayName(model)) },
                        supportingContent = { SourceBadge(model) },
                    )
                }
            }

            // Capabilities — one switch per SUPPORTED capability
            item {
                CardGroup(title = { Text(stringResource(R.string.models_capabilities)) }) {
                    model.capabilities.sortedBy { it.name }.forEach { cap ->
                        item(
                            headlineContent = { Text(cap.name.lowercase()) },
                            trailingContent = {
                                Switch(
                                    checked = cap in model.enabledCapabilities,
                                    onCheckedChange = { enabled ->
                                        vm.setCapabilityEnabled(model.id, cap, enabled)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            // Used for
            if (usedFor.isNotEmpty()) {
                item {
                    CardGroup(title = { Text(stringResource(R.string.models_used_for)) }) {
                        usedFor.forEach { role ->
                            item(headlineContent = { Text(role.name.lowercase()) })
                        }
                    }
                }
            }

            // Status
            item {
                CardGroup(title = { Text(stringResource(R.string.models_status)) }) {
                    item(headlineContent = { Text(model.lifecycle.name.lowercase()) })
                }
            }

            // Source-specific details
            when (val source = model.source) {
                is ModelSource.Local -> {
                    item {
                        CardGroup(title = { Text(stringResource(R.string.models_local_details)) }) {
                            val sizeBytes = model.metadata["sizeBytes"]?.toLongOrNull()
                            if (sizeBytes != null) {
                                item(headlineContent = { Text(stringResource(R.string.models_storage)) }) {
                                    // headline + supporting via CardGroup item signature
                                }
                            }
                            item(headlineContent = { Text(stringResource(R.string.models_runtime)) })
                            model.metadata["path"]?.let { path ->
                                item(headlineContent = { Text(path) })
                            }
                        }
                    }
                }
                is ModelSource.Cloud -> {
                    item {
                        CardGroup(title = { Text(stringResource(R.string.models_source_details)) }) {
                            item(headlineContent = { Text(sourceDisplayName(model)) })
                            item(headlineContent = { Text(stringResource(R.string.models_remote_id), style = MaterialTheme.typography.labelMedium) })
                            item(headlineContent = { Text(source.remoteModelId) })
                            item(
                                headlineContent = {
                                    Text(
                                        stringResource(
                                            if (model.connected) R.string.models_connection_healthy
                                            else R.string.models_connection_unavailable,
                                        ),
                                    )
                                },
                            )
                            item(
                                headlineContent = { Text(stringResource(R.string.models_provider_settings)) },
                                onClick = { navController.navigate(Screen.SettingProviderDetail(source.providerId)) },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                    }
                }
            }

            // Advanced
            item {
                CardGroup(title = { Text(stringResource(R.string.models_advanced)) }) {
                    item(headlineContent = { Text(stringResource(R.string.setting_provider_page_model_id)) })
                    item(headlineContent = { Text(model.id) })
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.local_llm_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.local_llm_rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameLocalModel(model.id, renameText)
                        renaming = false
                    },
                    enabled = renameText.isNotBlank() && renameText != model.displayName,
                ) {
                    Text(stringResource(R.string.local_llm_rename_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text(stringResource(R.string.local_llm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.local_llm_delete_confirm_message, model.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLocalModel(model.id)
                    pendingDelete = false
                    navController.popBackStack()
                }) {
                    Text(stringResource(R.string.local_llm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
```

**Notes for implementer:**
- `model.enabledCapabilities` must be observed live — the code above reads a snapshot from `collectAsState` on `allModels`, which re-emits after `setCapabilityEnabled`, so the switch reflects the new state. Verify `allModels` flow re-emits on capability change (it does — registry is the single source).
- The `Local details` CardGroup's size display needs a proper CardGroup `item(headlineContent, supportingContent)` call — follow the exact CardGroup signature found in existing files. Convert `sizeBytes` to a human string (KB/MB/GB) via a tiny local helper or the existing `local_llm_size_format` if present.
- `sourceDisplayName(model)` is imported from `components.SourceBadge` (Task 3).

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL on missing R.string keys (Task 11) only; fix any signature issues with `CardGroup`/`ModelAssignmentsSection` now.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelDetailPage.kt
git commit -m "feat: add model-centric detail page"
```

---

### Task 11: Strings — add `models_*` keys

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: every `R.string.models_*` referenced in Tasks 7, 9, 10.
- Produces: the exact keys below (English only; translations via locale-tui afterward).

- [ ] **Step 1: Add the keys**

Append inside `<resources>` (anywhere; keep alphabetical near other model keys is optional):

```xml
<string name="models_title">Models</string>
<string name="models_add">Add to Models</string>
<string name="models_used_by_default">Used by default</string>
<string name="models_show_all_assignments">Show all assignments</string>
<string name="models_hide_assignments">Hide assignments</string>
<string name="models_your_models">Your models</string>
<string name="models_sources">Sources</string>
<string name="models_manage_sources">Manage sources</string>
<string name="models_on_this_device">On this device</string>
<string name="models_source_configured_count">Configured · %1$d models</string>
<string name="models_source_disabled">Disabled</string>
<string name="models_source_not_configured">Not configured</string>
<string name="models_add_on_device">On device</string>
<string name="models_add_on_device_subtitle">Import a model file, download from URL, or browse supported models.</string>
<string name="models_add_connect_source">Connect a source</string>
<string name="models_add_connect_source_subtitle">Add an API provider or a local endpoint.</string>
<string name="models_filter_all">All</string>
<string name="models_filter_chat">Chat</string>
<string name="models_filter_vision">Vision</string>
<string name="models_filter_image">Image</string>
<string name="models_filter_audio">Audio</string>
<string name="models_filter_retrieval">Retrieval</string>
<string name="models_role_chat">Chat</string>
<string name="models_role_vision">Vision</string>
<string name="models_role_ocr">OCR</string>
<string name="models_role_image_generation">Image generation</string>
<string name="models_role_image_editing">Image editing</string>
<string name="models_role_text_to_speech">Text to speech</string>
<string name="models_role_speech_to_text">Speech to text</string>
<string name="models_role_embeddings">Embeddings</string>
<string name="models_capabilities">Capabilities</string>
<string name="models_used_for">Used for</string>
<string name="models_status">Status</string>
<string name="models_source_section">Source</string>
<string name="models_source_details">Source details</string>
<string name="models_local_details">Local details</string>
<string name="models_storage">Storage</string>
<string name="models_runtime">Runtime</string>
<string name="models_remote_id">Remote ID</string>
<string name="models_provider_settings">Provider settings</string>
<string name="models_connection_healthy">Healthy</string>
<string name="models_connection_unavailable">Unavailable</string>
<string name="models_advanced">Advanced</string>
```

Also add the Settings-home Models entry keys (used by Task 12):
```xml
<string name="setting_home_models">Models</string>
<string name="setting_home_models_desc">Manage available models, default assignments, and sources</string>
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS (all `models_*`/`setting_home_models*` references resolve).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add strings for consolidated Models page"
```

---

### Task 12: Routes + compat aliases — `Screen` and `RouteActivity`

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` (Screen sealed class ~788-817; `entry<Screen.X>` blocks ~507-539; imports ~129-130)

**Interfaces:**
- Consumes: `ModelsPage`, `ModelDetailPage` (Tasks 9, 10). Keeps `SettingProviderDetailPage`.
- Produces: new `Screen.Models` + `Screen.ModelDetail`; deprecated aliases `SettingModelManager`/`SettingDefaultModels`/`SettingProvider` now render `ModelsPage`.

- [ ] **Step 1: Add the new Screen types**

In the `Screen` sealed class (near line 812-817), add:

```kotlin
@Serializable
data object Models_Placeholder : Screen
```

Remove that placeholder — replace with:

```kotlin
@Serializable
data class Models(
    val request: me.rerere.rikkahub.ui.pages.models.ModelManagerRequest = me.rerere.rikkahub.ui.pages.models.ModelManagerRequest(),
    val showAssignments: Boolean = false,
    val scrollToSources: Boolean = false,
) : Screen

@Serializable
data class ModelDetail(val modelId: String) : Screen
```

- [ ] **Step 2: Update the route entries**

Replace the three blocks (507-509 SettingProvider, 520-522 SettingDefaultModels, 537-539 SettingModelManager) with:

```kotlin
// Deprecated compat alias — kept for restore-compat; no new code targets it.
entry<Screen.SettingProvider> {
    ModelsPage(scrollToSources = true)
}

entry<Screen.SettingProviderDetail> { key ->
    val id = Uuid.parse(key.providerId)
    SettingProviderDetailPage(id = id)
}

// Deprecated compat alias — kept for restore-compat.
entry<Screen.SettingDefaultModels> {
    ModelsPage(showAssignments = true)
}

// Deprecated compat alias — kept for restore-compat.
entry<Screen.SettingModelManager> { key ->
    ModelsPage(request = key.request)
}

entry<Screen.Models> { key ->
    ModelsPage(request = key.request, showAssignments = key.showAssignments, scrollToSources = key.scrollToSources)
}

entry<Screen.ModelDetail> { key ->
    ModelDetailPage(modelId = key.modelId)
}
```

- [ ] **Step 3: Fix imports**

`SettingProviderPage` and `DefaultModelsPage`/`ModelManagerPage` imports become unused once their entries are replaced. `ModelsPage`/`ModelDetailPage` imports are added (`me.rerere.rikkahub.ui.pages.models.ModelsPage`, `me.rerere.rikkahub.ui.pages.models.ModelDetailPage`). Do NOT delete the old import lines in this task if any call site still uses them (Task 13 deletes the pages; do the import cleanup there).

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS — old entries still import `SettingProviderPage`/`DefaultModelsPage`/`ModelManagerPage` so they must still compile (they're not deleted until Task 13).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/RouteActivity.kt
git commit -m "feat: route Models and ModelDetail with compat aliases"
```

---

### Task 13: Repoint live call sites

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` (:149-163 modelManager/defaultModels, :181-188 providers, `ProviderConfigWarningCard` ~:628-663)
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/generation/ImgGenPage.kt` (:394, :513)
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantBasicPage.kt` (:175)
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelList.kt` (:709)
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` (ProviderConfigure entry :1395 in spec) — verify actual file; spec lists `ProviderConfigure.kt :1395`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/ErrorCard.kt` (:168)
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DoctorScreen.kt` (:272), `DoctorChecks.kt` (:809/:852/:879/:909), `DoctorModels.kt` (:76)

**Interfaces:**
- Consumes: new `Screen.Models` / `Screen.ModelDetail`.
- Produces: all live navigation targets the new routes; zero references to deleted pages remain (checked by grep).

- [ ] **Step 1: Replace SettingPage AI entries**

Replace the three `SettingsHomeItem`s (modelManager :149, defaultModels :157, providers :181) with ONE:

```kotlin
SettingsHomeItem(
    id = "models",
    title = stringResource(R.string.setting_home_models),
    description = stringResource(R.string.setting_home_models_desc),
    icon = HugeIcons.Deepseek,
    keywords = listOf("model", "provider", "default", "download", "local", "cloud", "litert", "endpoint", "api"),
    onClick = { navController.navigate(Screen.Models()) },
),
```

Keep `agentSettings`, `assistants`, `promptLibrary`, `translateBubble` unchanged.

- [ ] **Step 2: Replace ProviderConfigWarningCard navigation**

In `ProviderConfigWarningCard` (SettingPage ~:653), change `Screen.SettingProvider` to `Screen.Models(scrollToSources = true)`.

- [ ] **Step 3: Repoint remaining call sites**

For each file, replace the old target with the equivalent new route:

| File:line | Old | New |
|-----------|-----|-----|
| `ImgGenPage.kt:394` | `Screen.SettingModelManager(request=...)` | `Screen.Models(request=...)` |
| `ImgGenPage.kt:513` | `Screen.SettingModelManager(request=...)` | `Screen.Models(request=...)` |
| `AssistantBasicPage.kt:175` | `Screen.SettingModelManager(...)` | `Screen.Models(request=...)` |
| `ModelList.kt:709` | `Screen.SettingProviderDetail(...)` | unchanged (kept) — verify; if it targets `Screen.SettingModelManager`, repoint to `Screen.Models(...)` |
| `ProviderConfigure` (wherever the `Screen.SettingModelManager()` call lives, spec `ProviderConfigure.kt:1395`) | `Screen.SettingModelManager()` | `Screen.Models()` |
| `ErrorCard.kt:168` | `Screen.SettingDefaultModels` | `Screen.Models(showAssignments = true)` |
| `DoctorScreen.kt:272` | `AppRouteKey.SettingProvider` | `Screen.Models(scrollToSources = true)` (or the AppRouteKey equivalent → `Screen.Models`) |
| `DoctorChecks.kt:809/852/879/909` | `AppRouteKey.SettingProvider` | `Screen.Models(scrollToSources = true)` |
| `DoctorModels.kt:76` | list containing `SettingProvider` | replace with `Screen.Models` |

Read each site at implementation time (spec line numbers are from the spec's exploration and may drift). Preserve any `ModelManagerRequest` arguments (`tab`, `providerId`, `search`) by passing them through as `request = ...` so anchor deep links still work.

- [ ] **Step 4: Verify no stale targets remain**

Run:
```bash
rg -n "Screen\.SettingModelManager|Screen\.SettingDefaultModels|Screen\.SettingProvider\b|SettingProviderPage\(|DefaultModelsPage\(|ModelManagerPage\(" app/src/main/java
```
Expected: only the intentional compat-route entries in `RouteActivity.kt` remain (the `Screen.SettingProvider` type declaration + its `entry<Screen.SettingProvider>` alias), plus any intentional references. No call sites may target the deleted pages' composables (`SettingProviderPage(`, `DefaultModelsPage(`, `ModelManagerPage(`) except where they are actually used. If `ModelManagerPage.kt`/`DefaultModelsPage.kt`/`SettingProviderPage.kt` still have references outside RouteActivity, fix them.

- [ ] **Step 5: Compile + test**

Run: `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.models.*" :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt app/src/main/java/me/rerere/rikkahub/ui/pages/generation/ImgGenPage.kt app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantBasicPage.kt app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelList.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt app/src/main/java/me/rerere/rikkahub/ui/components/ErrorCard.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DoctorScreen.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DoctorChecks.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/DoctorModels.kt
git commit -m "refactor: repoint call sites to consolidated Models route"
```

---

### Task 14: Delete absorbed pages + remove stale imports

**Files:**
- Delete: `app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerPage.kt`
- Delete: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/DefaultModelsPage.kt`
- Delete: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` (remove now-unused imports :129-130; add `ModelsPage`/`ModelDetailPage` imports)

**Interfaces:**
- Consumes: completed Tasks 7, 8, 9 (functionality fully moved).
- Produces: repo with no references to the deleted pages.

**Precondition check — verify before deleting:**
```bash
rg -n "SettingProviderPage\(|DefaultModelsPage\(|ModelManagerPage\(" app/src/main/java
```
Expected: zero results. If any remain, Task 13 was incomplete — do not delete until zero.

- [ ] **Step 1: Remove the three files**

```bash
rm app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerPage.kt app/src/main/java/me/rerere/rikkahub/ui/pages/models/DefaultModelsPage.kt app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt
```

- [ ] **Step 2: Fix RouteActivity imports**

Replace `import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage` and `import me.rerere.rikkahub.ui.pages.modelmanager.ModelManagerPage` / `...DefaultModelsPage` (if present) with `import me.rerere.rikkahub.ui.pages.models.ModelsPage` and `import me.rerere.rikkahub.ui.pages.models.ModelDetailPage`. Keep `import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage`.

- [ ] **Step 3: Compile + test**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 4: Verify `ModelManagerViewModel` still reachable**

`AddToModelsSheet` (Task 7) uses `koinViewModel<ModelManagerViewModel>()`; the VM class lives in `ui/pages/modelmanager/` package but its OWN file is `ModelManagerViewModel.kt` — confirm it was NOT deleted (only `ModelManagerPage.kt` was). If `ModelManagerViewModel.kt` is in the same file as the page, extract the VM to its own file first (Task 7 Step 2 would have surfaced this). Verify:
```bash
ls app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/
```

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/me/rerere/rikkahub
git commit -m "refactor: remove absorbed Model Manager, Default Models, and Provider pages"
```

---

### Task 15: Full verification gate

**Files:** none (verification only).

- [ ] **Step 1: Unit tests + compile**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL; all tests pass, including:
- `ModelsFilterTest` (Task 1)
- `DefaultAssignmentsSummaryTest` (Task 2)
- `SourceBadgeTest` (Task 3)
- `UnifiedModelsViewModelTest` (pre-existing, green)
- `ModelAssignmentsSectionTest` (pre-existing, green)

- [ ] **Step 2: Lint**

Run: `./gradlew lint`
Expected: no new errors. Fix any errors introduced by this work; pre-existing warnings elsewhere are out of scope.

- [ ] **Step 3: Static sanity on deleted-route safety**

Run: `rg -n "Screen\.SettingProvider\b|Screen\.SettingDefaultModels|Screen\.SettingModelManager" app/src/main/java`
Expected: matches ONLY in `RouteActivity.kt` (the alias declarations + alias entries). Zero matches in `SettingPage.kt`, other pages, or components.

- [ ] **Step 4: Device install (preserving data)**

If a device is available: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (never uninstall / `pm clear`). Smoke-test: Settings → AI → Models renders; search hides furniture; chips filter; tapping a model opens detail; `＋` opens Add to Models; Manage sources opens the sheet; a provider row opens `SettingProviderDetail`.

- [ ] **Step 5: Implementation report**

Write a final report listing: completed work, remaining work (none within this plan), tests executed, tests not executed and why (instrumented/device tests skipped if no device), device verification, known risks (aliases pending restore-compat removal; `sizeBytes` computed at registry build time), and the commit(s) containing the work.

- [ ] **Step 6: Commit (report)**

```bash
git add docs/superpowers/plans/2026-08-17-models-consolidation.md 2>/dev/null; git add -A docs 2>/dev/null
git commit -m "docs: models consolidation implementation plan + report" 2>/dev/null || echo "no doc changes to commit"
```
