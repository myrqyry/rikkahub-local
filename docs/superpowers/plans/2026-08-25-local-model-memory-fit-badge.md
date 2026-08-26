# Local model memory-fit badge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a live, advisory memory-fit status for installed local LiteRT
models on the Models page using the same `MemoryGuard` policy enforced at
load time.

**Architecture:** Add complete file-size metadata to every local inventory
descriptor, then map a `ModelDescriptor` plus an optional available-memory
snapshot to a pure `ModelMemoryFit` result. `ModelsPage` owns a
lifecycle-bound five-second memory sampler and passes the snapshot to the
inventory section, which renders an accessible icon-plus-text badge without
changing switches, assignments, or runtime admission.

**Tech Stack:** Kotlin, Jetpack Compose, Android `ActivityManager`, JUnit,
existing `MemoryGuard` and model-registry types.

## Global Constraints

- Cover installed local LiteRT language models only; do not add image, TTS,
  speech-to-text, or embedding admission in this change.
- Reuse `MemoryGuard.decide(modelFileBytes, availMemBytes)`; do not create a
  second memory policy.
- The badge is advisory and must not disable switches or assignments.
- Refresh available memory every five seconds only while `ModelsPage` is
  visible.
- Missing, malformed, or non-positive `sizeBytes` produces `Unavailable`.
- Do not discover file sizes from paths in the UI.
- Preserve the existing `.gitignore` policy and stage only implementation files
  for each commit.

---

### Task 1: Complete local inventory size metadata

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/SettingsModelRegistry.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/modelregistry/ModelRegistryTest.kt`

**Interfaces:**
- Consumes: local inventory `(fileName, path)` pairs built by
  `SettingsModelRegistry`.
- Produces: every installed local descriptor exposes
  `metadata["sizeBytes"]` as the decimal `File(path).length()` value, matching
  registered local descriptors.

- [ ] **Step 1: Write the failing metadata test**

Extract the pure metadata construction into an `internal` helper so the file
size contract is testable without constructing Android stores:

```kotlin
internal fun localFileMetadata(path: String): Map<String, String> = mapOf(
    "path" to path,
    "sizeBytes" to File(path).length().toString(),
)
```

Add a temporary-file test in `ModelRegistryTest`:

```kotlin
@Test
fun localFileMetadataIncludesSizeBytes() {
    val file = kotlin.io.path.createTempFile("model", ".litertlm").toFile()
    try {
        file.writeBytes(ByteArray(1234))
        assertEquals(
            mapOf("path" to file.absolutePath, "sizeBytes" to "1234"),
            localFileMetadata(file.absolutePath),
        )
    } finally {
        file.delete()
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.data.modelregistry.ModelRegistryTest.localFileMetadataIncludesSizeBytes
```

Expected: compilation failure because `localFileMetadata` does not yet exist.

- [ ] **Step 3: Implement and reuse the helper**

Add the helper to `SettingsModelRegistry.kt`. Replace the existing registered
descriptor metadata builder's `path`/`sizeBytes` block with
`putAll(localFileMetadata(path))`, and replace the unregistered inventory
descriptor's `metadata = mapOf("path" to path)` with
`metadata = localFileMetadata(path)`.

- [ ] **Step 4: Run the focused test and registry tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.data.modelregistry.ModelRegistryTest
```

Expected: all `ModelRegistryTest` tests pass.

- [ ] **Step 5: Commit the metadata slice**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/modelregistry/SettingsModelRegistry.kt app/src/test/java/me/rerere/rikkahub/data/modelregistry/ModelRegistryTest.kt
git commit -m "feat(models): expose local model file sizes"
```

### Task 2: Add the pure memory-fit mapper

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelMemoryFit.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelMemoryFitTest.kt`

**Interfaces:**
- Consumes: `ModelDescriptor`, `ModelSource.Local`, `LocalRuntime.LiteRT`,
  `ModelLifecycle`, and an optional `availMemBytes`.
- Produces: `ModelMemoryFit` with `Checking`, `Unavailable`, `FitsNow`, and
  `NeedsMoreMemory` states.

- [ ] **Step 1: Write the failing mapper tests**

Define the expected public shape in tests:

```kotlin
@Test
fun `eligible model that fits is FitsNow`() {
    val model = localModel(sizeBytes = "700", runtime = LocalRuntime.LiteRT)
    assertEquals(ModelMemoryFit.FitsNow(700, 1000), model.memoryFit(1000))
}

@Test
fun `eligible model that does not fit carries admission numbers`() {
    val model = localModel(sizeBytes = "800", runtime = LocalRuntime.LiteRT)
    val result = model.memoryFit(1000)
    assertEquals(ModelMemoryFit.NeedsMoreMemory(800, 1000, 1143), result)
}

@Test
fun `missing snapshot is Checking for eligible model`() {
    assertEquals(
        ModelMemoryFit.Checking,
        localModel(sizeBytes = "700", runtime = LocalRuntime.LiteRT).memoryFit(null),
    )
}

@Test
fun `ineligible and malformed models are Unavailable`() {
    assertEquals(ModelMemoryFit.Unavailable, localModel(sizeBytes = null).memoryFit(1000))
    assertEquals(ModelMemoryFit.Unavailable, localModel(sizeBytes = "0").memoryFit(1000))
    assertEquals(ModelMemoryFit.Unavailable, localModel(runtime = LocalRuntime.StableDiffusion).memoryFit(1000))
    assertEquals(ModelMemoryFit.Unavailable, localModel(lifecycle = ModelLifecycle.INSTALLED).memoryFit(1000))
    assertEquals(ModelMemoryFit.Unavailable, localModel(installed = false).memoryFit(1000))
}
```

Use a test helper that creates a `ModelDescriptor` with a local source, the
requested lifecycle/install/runtime overrides, and optional
`metadata["sizeBytes"]`. The helper must set `capabilities` to include
`ModelCapability.CHAT` so the descriptor represents an LLM.

- [ ] **Step 2: Run the mapper tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.ui.pages.models.ModelMemoryFitTest
```

Expected: compilation failure because the mapper and result types do not yet
exist.

- [ ] **Step 3: Implement the mapper**

Add this contract:

```kotlin
sealed interface ModelMemoryFit {
    data object Checking : ModelMemoryFit
    data object Unavailable : ModelMemoryFit
    data class FitsNow(val modelFileBytes: Long, val availMemBytes: Long) : ModelMemoryFit
    data class NeedsMoreMemory(
        val modelFileBytes: Long,
        val availMemBytes: Long,
        val requiredFreeBytes: Long,
    ) : ModelMemoryFit
}
```

Implement `ModelDescriptor.memoryFit(availMemBytes: Long?): ModelMemoryFit`
with this order: reject non-LiteRT, not-installed, non-ready, missing, or
non-positive size as `Unavailable`; return `Checking` for an eligible model
with a null snapshot; otherwise call `MemoryGuard.decide` and map `Ok` to
`FitsNow` and `TooLarge` to `NeedsMoreMemory`.

- [ ] **Step 4: Run the mapper and existing Models tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.ui.pages.models.ModelMemoryFitTest --tests me.rerere.rikkahub.ui.pages.models.UnifiedModelsViewModelTest
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit the pure admission slice**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelMemoryFit.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelMemoryFitTest.kt
git commit -m "feat(models): map local model memory admission"
```

### Task 3: Render the accessible inventory badge

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelMemoryFitBadge.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/components/ModelMemoryFitBadgeTest.kt`

**Interfaces:**
- Consumes: `ModelMemoryFit` from Task 2.
- Produces: a compact icon-plus-text Composable that exposes the same status
  through `contentDescription` and renders required/available values for
  `NeedsMoreMemory`.

- [ ] **Step 1: Add resource strings and a pure label test**

Add these strings to `strings.xml`:

```xml
<string name="models_memory_fit_now">Fits now</string>
<string name="models_memory_needs_more">Needs more memory · %1$d MB required, %2$d MB available</string>
<string name="models_memory_checking">Checking memory fit</string>
<string name="models_memory_unavailable">Memory fit unavailable</string>
```

Keep formatting in a small pure helper in the badge file:

```kotlin
internal fun ModelMemoryFit.labelRes(): Int = when (this) {
    is ModelMemoryFit.FitsNow -> R.string.models_memory_fit_now
    is ModelMemoryFit.NeedsMoreMemory -> R.string.models_memory_needs_more
    ModelMemoryFit.Checking -> R.string.models_memory_checking
    ModelMemoryFit.Unavailable -> R.string.models_memory_unavailable
}

internal fun ModelMemoryFit.labelArgs(): Array<Any> = when (this) {
    is ModelMemoryFit.NeedsMoreMemory -> arrayOf(
        requiredFreeBytes / 1_000_000,
        availMemBytes / 1_000_000,
    )
    else -> emptyArray()
}
```

Render the label with `stringResource(fit.labelRes(), *fit.labelArgs())`. Test
that each state maps to the intended resource ID and that the
`NeedsMoreMemory` formatter receives required and available megabytes.

- [ ] **Step 2: Implement the badge Composable**

Implement `ModelMemoryFitBadge(fit: ModelMemoryFit, modifier: Modifier = Modifier)`
using a `Row`, a 16 dp `Icon`, and a `Text`. Use `CheckmarkCircle02` for
`FitsNow`, `Alert01` for `NeedsMoreMemory`, `Clock02` for `Checking`, and
`Alert01` for `Unavailable`. Set the same localized label as the icon
`contentDescription`; use success, error, and on-surface-variant colors
respectively.

- [ ] **Step 3: Place the badge in each local inventory row**

Add `memoryFit: (ModelDescriptor) -> ModelMemoryFit = { ModelMemoryFit.Unavailable }`
to `ModelInventorySection`. In each row's `supportingContent`, retain the
existing capability/status content and append `ModelMemoryFitBadge` only when
the model is local LiteRT. Do not alter the switch's `checked`, `enabled`, or
`onCheckedChange` behavior.

- [ ] **Step 4: Run the badge tests and compile the app module**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.ui.pages.models.components.ModelMemoryFitBadgeTest :app:compileDebugKotlin
```

Expected: selected tests pass and Kotlin compilation succeeds.

- [ ] **Step 5: Commit the badge slice**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelMemoryFitBadge.kt app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt app/src/main/res/values/strings.xml app/src/test/java/me/rerere/rikkahub/ui/pages/models/components/ModelMemoryFitBadgeTest.kt
git commit -m "feat(models): show memory fit status"
```

### Task 4: Add the live Models-page memory snapshot

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPage.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelMemorySnapshotTest.kt`

**Interfaces:**
- Consumes: `ModelDescriptor.memoryFit`, `ModelInventorySection.memoryFit`,
  and Android `ActivityManager.MemoryInfo`.
- Produces: a five-second, composition-scoped `Long?` available-memory
  snapshot passed to every visible inventory row.

- [ ] **Step 1: Write the snapshot helper test**

Keep Android system access at the page boundary and put the loop timing in a
small suspend helper with an injected reader and delay function:

```kotlin
internal suspend fun sampleAvailableMemory(
    read: () -> Long,
    delayMillis: suspend (Long) -> Unit,
    publish: (Long) -> Unit,
) {
    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
        publish(read())
        delayMillis(5_000L)
    }
}
```

Test that the first sample is published immediately and the delay receives
`5_000L`. The test must cancel the coroutine after the first sample so it does
not create a real-time wait.

- [ ] **Step 2: Run the snapshot test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests me.rerere.rikkahub.ui.pages.models.ModelMemorySnapshotTest
```

Expected: compilation failure because `sampleAvailableMemory` does not yet
exist.

- [ ] **Step 3: Implement the composition-scoped sampler**

In `ModelsPage`, obtain `LocalContext.current.applicationContext`, create
`var availMemBytes by remember { mutableStateOf<Long?>(null) }`, and launch:

```kotlin
LaunchedEffect(Unit) {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    sampleAvailableMemory(
        read = {
            ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo).availMem
        },
        delayMillis = ::delay,
        publish = { availMemBytes = it },
    )
}
```

Pass the derived function to `ModelInventorySection`:

```kotlin
memoryFit = { model -> model.memoryFit(availMemBytes) }
```

`LaunchedEffect` cancellation when `ModelsPage` leaves composition supplies the
lifecycle boundary; no process-wide polling or persisted memory state is
allowed.

- [ ] **Step 4: Run the full relevant test set**

Run:

```bash
./gradlew :app:testDebugUnitTest :local-llm:testDebugUnitTest
```

Expected: both modules' unit tests pass.

- [ ] **Step 5: Inspect the final diff and commit the wiring**

```bash
git diff --check
git status --short
git add app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPage.kt app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelMemorySnapshotTest.kt
git commit -m "feat(models): refresh live memory fit status"
```

Expected: only the intended Models page and snapshot-test files are staged for
this commit.

## Final verification

After all tasks, run the complete application unit-test gate and inspect the
worktree:

```bash
./gradlew test
git status --short --branch
git log -4 --oneline --decorate
```

The build must pass, the branch must contain only intentional changes, and the
existing ignored planning directories must remain ignored except for the
already committed design and plan files.
