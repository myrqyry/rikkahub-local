# Unified model and provider management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move model installation into a modality-first Models catalog, route
each model to its authoritative runtime, and reduce Providers to a compact
configuration list.

**Architecture:** Add a small catalog/routing layer that describes modality,
runtime, installation kind, and registration target for each model. Reuse the
existing `ModelInstall`, `SettingLocalLlmViewModel`, `ModelManagerViewModel`,
and `QwenSemanticModelManager` operations behind that layer. Refactor the
existing Models and Providers surfaces to consume the catalog and provider
selection state without changing installed-data locations.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel/state flows,
ObjectBox-backed settings, OkHttp, existing local-llm runtime managers, and
JUnit tests.

## Implementation Status

- Tasks 1 and 2 are complete and committed as `fa00e21a` and `4466d10a`.
- Task 3 currently provides the FLUX package import entry point in the existing
  Models acquisition sheet, with validation, staging, backup, rollback, and
  centralized LiteRT registration.
- Task 4 removes local model lifecycle controls from provider configuration,
  hides the redundant local-provider model tab, enables LiteRT by default, and
  filters the provider source list to configured providers.
- Remaining: full modality catalog integration on `ModelsPage`, a dedicated
  provider type picker, full regression test coverage, and device smoke testing.

## Global Constraints

- LiteRT is a built-in provider and starts enabled.
- The Models screen groups models by modality, not runtime.
- The UI never asks the user to select a runtime.
- Incompatible runtime loading and registration are rejected.
- Existing model storage, resumable downloads, validation, atomic promotion,
  rollback, and provider identifiers remain authoritative.
- Installed application data must be preserved during verification.
- Do not expose FLUX editing controls in this change.
- Do not add a second model storage or download implementation.

---

### Task 1: Establish catalog and routing contracts

**Files:**
- Create: `local-llm/src/main/java/me/rerere/locallm/ModelCatalog.kt`
- Create: `local-llm/src/test/java/me/rerere/locallm/ModelCatalogTest.kt`
- Modify: `local-llm/src/main/java/me/rerere/locallm/litert/image/Flux2KleinPackage.kt`

**Interfaces:**
- Produces `ModelCatalogEntry`, `ModelModality`, `ModelRuntime`, and
  `ModelInstallKind` values consumed by app model-management code.
- Produces `ModelCatalog.entries`, including `FLUX.2-klein` as an Image Models
  LiteRT multi-file package.
- Produces a pure `ModelRouting.resolve(entry): ModelRoute` operation that
  rejects an incompatible provider/runtime pair.

- [ ] **Step 1: Write failing catalog and routing tests**

Add tests that assert FLUX metadata, modality grouping, runtime selection,
single-file versus package install kinds, and rejection of a mismatched route.
Use the existing test style and keep the tests independent of Android storage.

- [ ] **Step 2: Run the focused local-llm tests**

Run:

```bash
./gradlew :local-llm:test --tests '*ModelCatalogTest'
```

Expected: the new tests fail because the catalog and routing contracts do not
exist.

- [ ] **Step 3: Implement the minimum catalog and route types**

Define immutable data types with explicit runtime and installation kind fields.
Expose a single catalog list and derive modality sections by filtering that
list. Use `Flux2KleinPackage` as the package validation source rather than
duplicating required filenames or lookup rules.

- [ ] **Step 4: Run the focused tests again**

Run the same Gradle command and confirm all catalog and routing tests pass.

- [ ] **Step 5: Commit the catalog seam**

```bash
git add local-llm/src/main/java/me/rerere/locallm/ModelCatalog.kt \
  local-llm/src/main/java/me/rerere/locallm/litert/image/Flux2KleinPackage.kt \
  local-llm/src/test/java/me/rerere/locallm/ModelCatalogTest.kt
git commit -m "feat(models): add modality catalog routing"
```

### Task 2: Add authoritative registration orchestration

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelRegistration.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelRegistrationTest.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerViewModel.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/SettingLocalLlmViewModel.kt`

**Interfaces:**
- `ModelRegistration.register(...)` consumes a catalog route, validated file
  or package, settings update function, and installed-model preferences.
- It produces exactly one provider model record for a successful installation,
  enables the matching built-in local provider, and rejects mismatches before
  persistence.
- Existing managers remain the owners of transfer, validation, and disk
  promotion; this task only centralizes route-to-provider registration.

- [ ] **Step 1: Write failing registration tests**

Cover LiteRT registration, Stable Diffusion registration, llama.cpp GGUF
classification, duplicate model replacement, provider enablement, and a
mismatched route that leaves preferences and settings unchanged.

- [ ] **Step 2: Run the focused app tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ModelRegistrationTest'
```

Expected: the tests fail because registration is currently duplicated across
the two ViewModels.

- [ ] **Step 3: Extract the shared registration operation**

Move only the common provider/model creation and runtime compatibility checks
into `ModelRegistration`. Preserve existing provider IDs, model capability
derivation, and storage paths. Keep download progress and file operations in
their current managers.

- [ ] **Step 4: Delegate existing manager completion paths**

Change the `Done` paths in both ViewModels to call the shared registration
operation. Ensure package promotion occurs before registration and failed
registration cannot leave a newly advertised incomplete model.

- [ ] **Step 5: Run focused and existing manager tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ModelRegistrationTest' \
  --tests '*ModelManagerViewModelTest' \
  --tests '*SettingLocalLlmViewModelTest'
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit the registration seam**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelRegistration.kt \
  app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerViewModel.kt \
  app/src/main/java/me/rerere/rikkahub/ui/pages/setting/locallm/SettingLocalLlmViewModel.kt \
  app/src/test/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelRegistrationTest.kt
git commit -m "feat(models): centralize local model registration"
```

### Task 3: Move installation entry points into the Models catalog

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerViewModel.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelCatalogInstallTest.kt`

**Interfaces:**
- The Models ViewModel exposes catalog entries grouped by
  `ModelModality`, install state, active progress, and errors.
- `install(entry)` dispatches to the existing single-file or multi-file
  manager based on `ModelInstallKind`; no runtime parameter is accepted from
  the UI.
- The UI renders Image Models, including FLUX.2-klein, and presents ready,
  incomplete, failed, and unavailable states.

- [ ] **Step 1: Write failing install-dispatch tests**

Assert that FLUX dispatches to the resumable package manager, single-file
LiteRT dispatches to `ModelInstall`, GGUF dispatches to the existing GGUF
manager, and no dispatcher accepts a user-selected runtime.

- [ ] **Step 2: Run the focused tests and inspect current Models navigation**

Run the focused test command, then inspect the current Models page and route
entry point so the catalog is added to the existing surface rather than a new
navigation path.

- [ ] **Step 3: Implement catalog-backed state and dispatch**

Reuse existing progress/error state where possible. Add only the catalog entry
and install-kind dispatch needed to connect FLUX and the existing managers.
Keep package partial data resumable and expose only promoted packages as ready.

- [ ] **Step 4: Render modality sections and install controls**

Replace runtime-oriented model choices with modality sections. Put FLUX under
Image Models. Keep controls compact on phone-sized layouts and show runtime as
secondary metadata only when useful; never make it a choice.

- [ ] **Step 5: Add user-facing strings and UI ownership assertions**

Add concise strings for modality headings, install states, and package errors.
Test the catalog's sections and dispatch behavior without snapshot-heavy UI
infrastructure unless the existing project already uses it for this page.

- [ ] **Step 6: Run focused tests and compile the app module**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```

Expected: tests pass and Kotlin compilation succeeds.

- [ ] **Step 7: Commit the Models catalog**

```bash
  git add app/src/main/java/me/rerere/rikkahub/ui/pages/modelmanager/ModelManagerViewModel.kt \
  app/src/main/java/me/rerere/rikkahub/ui/pages/models/ModelsPage.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/me/rerere/rikkahub/ui/pages/models/ModelCatalogInstallTest.kt
git commit -m "feat(models): manage local models from catalog"
```

### Task 4: Reduce Providers to configured-provider management

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- Create: `app/src/test/java/me/rerere/rikkahub/ui/pages/setting/ProviderSurfaceTest.kt`

**Interfaces:**
- The Providers screen exposes configured/enabled provider rows and an Add
  provider type picker.
- LiteRT is materialized as enabled without requiring model installation.
- `ProviderConfigure` retains provider-level fields and diagnostics but has no
  model URLs, catalog download buttons, installed model rows, or model delete
  actions.

- [ ] **Step 1: Write failing provider ownership tests**

Assert that fresh settings include enabled LiteRT, provider listing filters out
unconfigured types, Add provider presents types, and `ProviderConfigure` no
longer exposes model-management actions.

- [ ] **Step 2: Run the focused provider tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*Provider*Test'
```

Expected: tests fail against the current expanded provider surface.

- [ ] **Step 3: Remove model-management content from provider settings**

Delete the URL install controls, recommended model picker, installed model
rows, and model lifecycle actions from `ProviderConfigure`. Remove the provider
detail model-management tab and its add/edit/delete model controls from
`SettingProviderDetailPage`. Preserve provider name, enabled state, health,
and required local-runtime diagnostics.

- [ ] **Step 4: Implement compact provider listing and type picker**

In `SettingPage.kt`, render only providers present in settings and enabled or
explicitly configured. Use existing provider copy/add helpers and stable IDs.
Selecting a type opens `SettingProviderDetailPage` for that provider rather
than expanding every provider inline.

- [ ] **Step 5: Ensure LiteRT default materialization is idempotent**

Add LiteRT only when absent, with `enabled = true`; never overwrite existing
LiteRT models, names, or paths. Add a regression test for repeated settings
initialization.

- [ ] **Step 6: Run provider tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*Provider*Test' \
  :app:compileDebugKotlin
```

Expected: all selected tests pass and compilation succeeds.

- [ ] **Step 7: Commit the compact provider surface**

```bash
  git add app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderConfigure.kt \
  app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt \
  app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt \
  app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt \
  app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt \
  app/src/test/java/me/rerere/rikkahub/ui/pages/setting/ProviderSurfaceTest.kt
git commit -m "feat(settings): compact provider management"
```

### Task 5: Full verification and device smoke test

**Files:**
- Modify: none unless verification finds a regression.
- Verify: all files changed by Tasks 1 through 4.

- [ ] **Step 1: Run all local-llm tests**

```bash
./gradlew :local-llm:test
```

- [ ] **Step 2: Run all app unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

- [ ] **Step 3: Build the debug APK**

```bash
./gradlew assembleDebug -x :web:installWebUiDeps
```

If the web dependency task is available, also run the unexcluded build. Record
the environment limitation if Bun remains unavailable.

- [ ] **Step 4: Install without clearing application data**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app, open Providers, confirm the compact list and LiteRT default,
open Models, confirm Image Models and FLUX.2-klein, and verify no existing
installed model disappears.

- [ ] **Step 5: Inspect the final diff and status**

```bash
git diff HEAD~4 --check
git status --short --branch
```

Record completed work, tests executed, tests not executed and why, device
verification, known risks, and commits in the final implementation report.
