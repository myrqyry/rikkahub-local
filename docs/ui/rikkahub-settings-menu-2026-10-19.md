# RikkaHub settings: visible category menu (2026-10-19)

## Why

The previous settings home hid six categories behind a horizontal LazyRow
and showed oversized, padded square cards for the active section.
The user explicitly rejected both hidden sideways navigation and avoidable
vertical wasted space. This is a navigation correction, not a blanket demand
to shrink touch targets or text.

## Implementation

- Removed the horizontal chip strip and 3-column destination-card grid.
- Settings home shows all six category names **at once** as compact vertical
  rows with a leading icon, sample destinations, and a trailing arrow.
- A selected category shows a compact, single-column list of its destinations
  with icons, one-line supporting text and clear navigation affordances.
- The top bar displays the category name and back returns to the overview;
  Android system back behaves identically.
- Search works across *all* sections even when inside a category, and is
  dismissible with back. Search matches word prefixes rather than arbitrary
  substrings; only a matching category *title* expands all its destinations.
  So searching "rag" doesn't match "storage" or dump unrelated settings.
- Preserved original destinations, conditional developer-only settings,
  not-configured warning, privacy and provider state, and existing search aliases.
- No new persistent state, migration, account changes, or permissions.
- 64dp category / min-56dp destination rows retain touch-friendly sizing while
  replacing wasted tile area; no horizontal scrolling or square card grids.

## Verification

- Gradle `./gradlew :app:testDebugUnitTest :app:assembleDebug`:
  BUILD SUCCESSFUL. 1,501 app JVM tests, 0 failures/errors/skips.
- Real Pixel 10 Pro debug APK installed **in-place** by `adb install -r`
  after verified, SHA-256 checked private backup of existing database,
  datastore settings and shared preferences. No app data reset or overwrite
  of model files.
- Inspected actual dark-theme screenshot and UI tree: all six categories
  visible without scrolling and zero horizontal chips. Category tap displays
  five compact AI & Models destinations; Android back returns to six categories.
- Tested search on the final installed APK: `rag` shows Knowledge & RAG
  without unrelated Storage or Browser matches. Regression unit test added.
- No push: the branch has unpublished history that is intentionally local.

## Boundaries

No light-theme/tablet/font-scale device coverage in this pass. Provider and
model detail screens are separate surfaces; this specifically changes the
settings **home menu** rather than those forms.
