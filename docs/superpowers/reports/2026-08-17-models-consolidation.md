# Models Consolidation — Implementation Report

**Date:** 2026-08-17
**Spec:** `docs/superpowers/specs/2026-08-17-models-consolidation-design.md` (commit 71452ecd)
**Plan:** `docs/superpowers/plans/2026-08-17-models-consolidation.md` (commit 302d22b7)
**Execution:** inline (superpowers:executing-plans), on `master` with explicit user consent

## Goal

Fold three Settings AI destinations (Model Manager, Default Models, Providers) into a single top-level **Models** page. Local/cloud becomes a source badge rather than the primary navigation structure; Providers becomes **Sources**; Default Models becomes the **Used by default** section.

## Completed work

| # | Task | Commit |
|---|------|--------|
| 1 | `ModelsFilter` capability taxonomy (ALL/CHAT/VISION/IMAGE/AUDIO/RETRIEVAL) + tests | 39ddc209 |
| 2 | `DefaultAssignmentsSummary` pure summary + tests | 38e5faa4 |
| 3 | `SourceBadge` + `sourceDisplayName` + tests | (SourceBadge commit) |
| 4 | Registry `descriptor()` adds `path`/`sizeBytes` metadata for local models | 8e1b6c36 |
| 5 | `UnifiedModelsViewModel.setCapabilityEnabled(modelId, capability, enabled)` | 6323b262 |
| 6 | Flattened `ModelInventorySection` to uniform cards | 35cdaeb4 |
| 7 | `AddToModelsSheet` (On device + Connect a source) | 87cd47b9 |
| 8 | `ManageSourcesSheet` (full-height provider management) | d6d826fe |
| 9 | `ModelsPage` (search, chips, Used by default, Your models, Sources) | 60a420d2 |
| 10 | `ModelDetailPage` (per-capability switches, local/cloud detail variants) | 3a9b4802 |
| 11 | `strings.xml` `models_*` keys (+ `setting_home_models`/`_desc`) | 87cd47b9 (early) |
| 12 | `Screen.Models`/`Screen.ModelDetail` routes + compat aliases | d078613c |
| 13 | Repointed all call sites to `Screen.Models` | 7b219c56 |
| 14 | Deleted `ModelManagerPage.kt`, `DefaultModelsPage.kt`, `SettingProviderPage.kt` | 02a3f5be |
| 15 | Lint baseline updated for new strings | ae4a90cd |

Preserved and reused as designed: `ModelRegistry`, `SettingsModelRegistry`, `UnifiedModelsViewModel`, `ModelAssignmentsSection`, `ModelManagerViewModel` (+ one thin `addProvider`), `SettingProviderDetailPage`, `ProviderConfigure`, `RECOMMENDED_PROVIDERS`. Deprecated `Screen.SettingModelManager`/`SettingDefaultModels`/`SettingProvider` route types retained as compat aliases rendering `ModelsPage` with anchors.

## Remaining work

- Translations for the ~45 new `models_*` / `setting_home_models` strings via `locale-tui` (lint-baseline suppresses MissingTranslation until then).
- Remove the three deprecated `Screen.*` compat aliases once restorable-route compatibility is proven.
- Optional: provider health/preflight so Source rows can show `Connected` instead of `Configured`/`Disabled`/`Not configured`.

## Tests executed

- `./gradlew :app:testDebugUnitTest` — all green (new: `ModelsFilterTest`, `DefaultAssignmentsSummaryTest`, `SourceBadgeTest`; existing `UnifiedModelsViewModelTest`, `ModelAssignmentsSectionTest` still green).
- `./gradlew :app:compileDebugKotlin` — clean (only pre-existing warnings).
- `./gradlew lint` — green (baseline updated for the new untranslated keys).
- `./gradlew :app:assembleDebug` — builds; APK at `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

## Tests not executed and why

- Instrumented UI tests (`connectedDebugAndroidTest`) — not run; verification performed via on-device smoke test instead.
- No new `ModelsPage`/`ModelDetailPage` composable tests — filtering/search/assignment logic is covered by the pure-function unit tests above; the pages are thin composition over verified units.

## Device verification

On device 56290DLCH002PE (package `excp.rikkahub.local.debug`, [DEV MODE] build), installed with `adb install -r` (data preserved):

- Settings → AI & Models shows a **single** `Models` entry (no Model Manager / Default Models / Providers).
- Models page renders: title, search field, ＋ add action, 6 filter chips, Used by default, Your models (with capability text + on-device badge), Sources (On this device + provider rows), Manage sources, Show all assignments.
- Searching `gemini` suppresses furniture (Used by default / Sources hidden), matching cards only; back clears.
- Model tap → `ModelDetailPage` (Source, Capabilities, Status, Source details with Remote ID + Provider settings, Advanced/Model ID).
- ＋ → `AddToModelsSheet` (On device: catalog / HF URL / local file; Connect a source → ProviderConfigure dialog).
- Manage sources → full-height `ManageSourcesSheet` with provider list, enable/disable, model counts, on-device rows, AiHubMix badge, requirement hints.
- Provider rows → `SettingProviderDetail` route intact.

## Known risks

- **Compat aliases**: the three deprecated `Screen.*` routes render ModelsPage with anchors; they exist as insurance against Navigation3 restoring old route state across an app update. Safe to remove once restore-compat is proven.
- **`sizeBytes` is computed at registry-build time** (`File(path).length()` inside `SettingsModelRegistry.descriptor()`), not a live probe; a file changing size after registry construction shows a stale value until the registry refreshes.
- **`{count} models` placeholder**: `unified_models_provider_count` renders the literal `{count} models` when count text is absent — this is the pre-existing locale-tui convention, unchanged from the old ModelManager behavior.
- **Unrelated WIP left untouched**: pre-existing uncommitted changes in `app/src/main/java/me/rerere/rikkahub/data/ai/` (RuntimeMemoryProfile, StableDiffusionProvider) and their tests remain unstaged.

## Commits

`71452ecd` (spec) · `302d22b7` (plan) · `39ddc209` · `38e5faa4` · SourceBadge commit · `8e1b6c36` · `6323b262` · `35cdaeb4` · `87cd47b9` · `d6d826fe` · `60a420d2` · `3a9b4802` · `d078613c` · `7b219c56` · `02a3f5be` · `ae4a90cd`
