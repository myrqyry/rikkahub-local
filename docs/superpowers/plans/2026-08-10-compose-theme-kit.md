# Compose Theme Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add seed-based theme families, persisted variation/accent selection,
and native Compose controls to the existing RikkaHub theme settings.

**Architecture:** Keep `RikkahubTheme`, `PresetTheme`, `Settings`, and custom
theme JSON as the compatibility seams. Add a small theme-family registry and a
pure selection/generation layer that produces Material 3 light/dark schemes;
the settings page persists only stable family, variation, accent, and seed
values.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 Expressive, AndroidX
DataStore preferences, kotlinx.serialization, Material color utilities, JUnit.

## Global Constraints

- Preserve existing Sakura, Ocean, Spring, Autumn, Black, and custom themes.
- Preserve custom-theme JSON import/export compatibility.
- Keep wallpaper Dynamic Color unchanged when no explicit Material You seed is set.
- Use long-press as the native equivalent of the web right-click shortcut.
- Do not add CSS variables, Tailwind, HTML inputs, or background-pattern dependencies.
- Use Material 3 semantic colors and accessible Compose controls.

---

### Task 1: Add the theme-family registry and pure selection rules

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/theme/ThemeFamily.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/theme/PresetTheme.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/theme/ThemeFamilyTest.kt`

**Interfaces:**
- Produce `data class ThemeVariation(id: String, primarySeed: Int, secondarySeed: Int, tertiarySeed: Int)`.
- Produce `data class ThemeAccent(id: String, seed: Int)`.
- Produce `data class ThemeFamily(id: String, name: @Composable () -> Unit, variations: List<ThemeVariation>, accents: List<ThemeAccent>)`.
- Produce `fun themeFamily(id: String): ThemeFamily?` and `val ThemeFamilies: List<ThemeFamily>`.
- Produce `fun nextThemeVariation(family: ThemeFamily, current: String): String` and `fun nextThemeAccent(family: ThemeFamily, current: String): String`.

- [ ] **Step 1: Add failing tests** for all five new families, wraparound variation/accent cycling, and unknown values falling back to the first entry.
- [ ] **Step 2: Run the focused test** with `./gradlew :app:testDebugUnitTest --tests '*ThemeFamilyTest'`; expect failure before implementation.
- [ ] **Step 3: Implement the registry** with the exact family names, variation names, and accent names from the approved spec. Use seed integers only in this internal registry; expose names through localized resource lambdas.
- [ ] **Step 4: Keep `PresetTheme` compatibility** by adding registry-generated themes to the existing lookup path without removing current preset objects or custom-theme lookup.
- [ ] **Step 5: Run the focused test** and expect all registry and cycling cases to pass.

### Task 2: Generate static schemes and persist theme selection

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/theme/ThemeFamily.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/theme/ThemePersistenceTest.kt`

**Interfaces:**
- Add `fun ThemeFamily.colorScheme(variationId: String, accentId: String, dark: Boolean): ColorScheme`.
- Add `Settings.themeVariation: String`, `Settings.themeAccent: String`, and `Settings.materialYouSourceColor: Long?` with compatible defaults.
- Add matching preference keys, read defaults, and writes.

- [ ] **Step 1: Add failing tests** for missing preference defaults, valid round-trip values, unknown-value fallback, and distinct light/dark generated schemes.
- [ ] **Step 2: Run `./gradlew :app:testDebugUnitTest --tests '*ThemePersistenceTest'`** and confirm failure.
- [ ] **Step 3: Implement scheme generation** using the repository's Material color utility. Use the selected accent seed as primary input and variation seeds for secondary/tertiary roles; generate readable light and dark schemes.
- [ ] **Step 4: Add preference serialization** using existing keys and `Settings` defaults. Do not change `CustomTheme` serialization.
- [ ] **Step 5: Update `RikkahubTheme`** so dynamic color uses the explicit Material You seed when non-null and retains platform wallpaper dynamic color when null; static mode resolves the selected family variation/accent before falling back to existing presets/custom themes.
- [ ] **Step 6: Run focused persistence tests** and then `./gradlew :app:testDebugUnitTest --tests '*ThemePersistenceTest'` expecting pass.

### Task 3: Add Compose settings controls and interaction shortcuts

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingThemePage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/PresetThemeButton.kt`
- Modify: `app/src/main/res/values/strings_settings_home.xml`
- Modify: localized settings string files that contain theme labels

**Interfaces:**
- `PresetThemeButtonGroup` accepts family selection, variation cycling, and accent cycling callbacks while retaining its existing `themeId` callback compatibility where practical.
- `SettingThemePage` writes `themeId`, `themeVariation`, `themeAccent`, and `materialYouSourceColor` through `SettingVM.updateSettings`.

- [ ] **Step 1: Add or extend Compose tests** for active-family reselection cycling variation and long-press cycling accent.
- [ ] **Step 2: Implement native controls** using existing Material 3 cards, buttons, chips, and the existing color editor pattern; expose selected state and labels to accessibility services.
- [ ] **Step 3: Add `combinedClickable` long-press handling** to the active theme selector; do not add browser-only right-click behavior.
- [ ] **Step 4: Show the Material You seed control when Dynamic Color is enabled**, preserving wallpaper behavior when the seed is cleared.
- [ ] **Step 5: Add localized names and labels** for all new families, variations, accents, and controls using the repository's existing localization workflow.
- [ ] **Step 6: Run app unit tests and inspect the settings page on a debug device** for touch targets, selected states, and dark/light readability.

### Task 4: Full verification and delivery

**Files:**
- Modify: `SESSION-STATE.md`

- [ ] **Step 1: Run `./gradlew :app:testDebugUnitTest --no-daemon`**.
- [ ] **Step 2: Run `./gradlew :speech:testDebugUnitTest :app:testDebugUnitTest :ai:testDebugUnitTest assembleDebug --no-daemon`**.
- [ ] **Step 3: Run `git diff --check` and verify no generated or unrelated files are staged.
- [ ] **Step 4: Update `SESSION-STATE.md` with the implemented theme registry, persistence keys, Compose controls, and test/build results.
- [ ] **Step 5: Commit with `feat(theme): add Compose Theme Kit themes`.
- [ ] **Step 6: Push the current branch to `origin/master`; use the documented `--no-verify` workaround only if the unsafe-ship hook reports its known false positives.
