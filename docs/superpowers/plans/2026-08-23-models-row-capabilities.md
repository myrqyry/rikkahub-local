# Models row capabilities implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each Models inventory row show authoritative capabilities and
meaningful provider or lifecycle status without changing model routing or
persistence.

**Architecture:** Keep `ModelDescriptor` as the only model data source. Add a
small UI-facing status classifier beside the existing capability UI mappings,
then render its result in `ModelInventorySection` below the capability row.
Retain the existing aggregate switch and detail-page per-capability controls.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, HugeIcons, Android Gradle
Plugin, JUnit.

## Global Constraints

- Render capabilities from `ModelDescriptor.capabilities`; do not infer them
  from display names in the UI.
- Do not add a new model or capability representation.
- Do not change model inference, selection, persistence, or provider routing.
- Preserve the aggregate switch behavior: checked when
  `enabledCapabilities` is non-empty; toggling changes all advertised
  capabilities.
- Keep per-capability controls on `ModelDetailPage`.
- Use existing localized strings and HugeIcons mappings where available.
- Keep the layout readable on phone-sized screens.
- Do not change local Stable Diffusion execution.

---

## File map

- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelCapabilityUi.kt`:
  Add the small UI-only status classification and reuse existing lifecycle and
  connection string mappings.
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt`:
  Render status beneath capabilities while preserving the current row and
  switch behavior.
- Create `app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelCapabilityUiTest.kt`:
  Test status precedence and the ready/connected no-status case.
- No resource file changes are expected because the required strings already
  exist in `app/src/main/res/values/strings.xml`.

## Task 1: Add status classification tests

**Files:**
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelCapabilityUiTest.kt`
- Read: `app/src/main/java/me/rerere/rikkahub/data/modelregistry/ModelRegistryModels.kt`

**Interfaces:**
- Consumes: `ModelDescriptor`, `ModelSource.Local`, `ModelSource.Cloud`,
  `ModelLifecycle`, and `inventoryStatus` from `ModelCapabilityUi.kt`.
- Produces: A failing test contract for the status precedence used by the
  inventory row.

- [ ] **Step 1: Write the failing tests**

Create model fixtures with `ModelDescriptor` and assert the classifier returns
the first applicable status:

```kotlin
package me.rerere.rikkahub.ui.pages.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import me.rerere.locallm.LocalRuntime
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource

class ModelCapabilityUiTest {
    @Test
    fun `disabled provider takes precedence`() {
        val model = descriptor(
            source = ModelSource.Cloud("openai", "gpt-image-1"),
            providerEnabled = false,
            connected = false,
        )

        assertEquals(ModelInventoryStatus.PROVIDER_DISABLED, model.inventoryStatus())
    }

    @Test
    fun `disconnected cloud model reports unavailable connection`() {
        val model = descriptor(
            source = ModelSource.Cloud("openai", "gpt-image-1"),
            connected = false,
        )

        assertEquals(ModelInventoryStatus.CONNECTION_UNAVAILABLE, model.inventoryStatus())
    }

    @Test
    fun `non-ready local model reports lifecycle status`() {
        val model = descriptor(
            source = ModelSource.Local(LocalRuntime.StableDiffusion),
            lifecycle = ModelLifecycle.INSTALLED,
        )

        assertEquals(ModelInventoryStatus.NOT_READY, model.inventoryStatus())
    }

    @Test
    fun `ready connected model has no inventory status`() {
        val model = descriptor(
            source = ModelSource.Cloud("openai", "gpt-image-1"),
            connected = true,
        )

        assertNull(model.inventoryStatus())
    }

    private fun descriptor(
        source: ModelSource = ModelSource.Cloud("openai", "gpt-image-1"),
        providerEnabled: Boolean = true,
        connected: Boolean = true,
        lifecycle: ModelLifecycle = ModelLifecycle.READY,
    ) = ModelDescriptor(
        id = "test-model",
        displayName = "Test model",
        source = source,
        capabilities = emptySet(),
        providerEnabled = providerEnabled,
        connected = connected,
        lifecycle = lifecycle,
    )
}
```

The fixture provides a valid descriptor with stable defaults; each test
overrides only the fields relevant to its precedence rule.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests \
  me.rerere.rikkahub.ui.pages.models.ModelCapabilityUiTest
```

Expected result: compilation fails because `ModelInventoryStatus` and
`inventoryStatus` do not exist yet.

## Task 2: Implement the status classifier and row rendering

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelCapabilityUi.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt`

**Interfaces:**
- Consumes: `ModelDescriptor` fields `providerEnabled`, `source`, `connected`,
  and `lifecycle`; existing `ModelLifecycle.labelRes`; existing model strings.
- Produces: `ModelInventoryStatus` and `ModelDescriptor.inventoryStatus()` for
  the inventory row, plus visible status text when the model is unavailable.

- [ ] **Step 1: Add the minimal classifier**

In `ModelCapabilityUi.kt`, add a UI-only enum and classifier:

```kotlin
enum class ModelInventoryStatus {
    PROVIDER_DISABLED,
    CONNECTION_UNAVAILABLE,
    NOT_READY,
}

fun ModelDescriptor.inventoryStatus(): ModelInventoryStatus? = when {
    !providerEnabled -> ModelInventoryStatus.PROVIDER_DISABLED
    source is ModelSource.Cloud && !connected -> ModelInventoryStatus.CONNECTION_UNAVAILABLE
    source is ModelSource.Local && lifecycle != ModelLifecycle.READY -> ModelInventoryStatus.NOT_READY
    else -> null
}
```

Add these imports:

```kotlin
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource
```

Use the existing string mappings in the composable rather than adding a new
localized data model.

- [ ] **Step 2: Run the focused test to verify it passes**

Run the same focused Gradle command from Task 1. Expected result: all four
status tests pass.

- [ ] **Step 3: Render status in the inventory row**

In `ModelInventorySection.kt`, retain `ModelCapabilityRow(model.capabilities)`
and the current provider-disabled branch, then add one status line based on the
classifier:

```kotlin
model.inventoryStatus()?.let { status ->
    Text(
        text = stringResource(
            when (status) {
                ModelInventoryStatus.PROVIDER_DISABLED -> R.string.models_source_disabled
                ModelInventoryStatus.CONNECTION_UNAVAILABLE -> R.string.models_connection_unavailable
                ModelInventoryStatus.NOT_READY -> model.lifecycle.labelRes
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (status == ModelInventoryStatus.PROVIDER_DISABLED ||
            status == ModelInventoryStatus.CONNECTION_UNAVAILABLE
        ) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
```

Do not render a duplicate provider-disabled line. Replace the existing
`!model.providerEnabled` branch with the classifier output, preserving its
error color. Leave the headline, `SourceBadge`, aggregate switch, click
handler, and capability row unchanged.

- [ ] **Step 4: Run focused tests and compile the app**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests \
  me.rerere.rikkahub.ui.pages.models.ModelCapabilityUiTest
./gradlew :app:assembleDebug
```

Expected result: both commands succeed. Existing deprecation or dependency
warnings do not count as failures; new compilation errors do.

## Task 3: Verify the Models page on the correct device

**Files:**
- No source changes.

**Interfaces:**
- Consumes: the debug APK and the installed registry data.
- Produces: evidence that the row remains readable and the status/capability
  hierarchy works on the Pixel 10 Pro (`56290DLCH002PE`, Android 17).

- [ ] **Step 1: Install without clearing app data**

Run:

```bash
adb -s 56290DLCH002PE install -r -d \
  app/build/outputs/apk/debug/app-universal-debug.apk
```

Use `adb install -r` only. Do not uninstall the app, clear data, or reset the
database.

- [ ] **Step 2: Open Models and inspect the inventory rows**

Use the existing Android UI verification workflow to confirm:

- Model names remain visually dominant and source badges remain visible.
- Capability icons and labels correspond to the registry capabilities.
- Disabled providers show `Disabled` without hiding their models.
- Disconnected cloud models show `Unavailable`.
- Non-ready local models show their lifecycle label.
- Ready, connected models do not gain unnecessary status noise.
- The aggregate switch still enables or disables the model.

- [ ] **Step 3: Run the relevant regression suite**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected result: the app unit-test suite passes. Instrumented tests are not
required unless a device UI regression is found during the manual check.

- [ ] **Step 4: Commit the implementation**

Stage only the implementation files and test file:

```bash
git add \
  app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelCapabilityUi.kt \
  app/src/main/java/me/rerere/rikkahub/ui/pages/models/components/ModelInventorySection.kt \
  app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelCapabilityUiTest.kt
git commit -m "feat(models): show model readiness status"
```

## Self-review checklist

- Spec coverage: goals, non-goals, authoritative capabilities, source badges,
  aggregate switch behavior, status precedence, tests, build, and phone-sized
  verification are covered by Tasks 1 through 3.
- Placeholder scan: no unresolved implementation placeholders remain.
- Type consistency: `ModelInventoryStatus` and `ModelDescriptor.inventoryStatus()`
  are introduced in Task 2 and consumed by the test in Task 1 and the row in
  Task 2.
- Scope: the plan changes only the existing UI mapping, inventory component,
  and a focused unit-test file.
