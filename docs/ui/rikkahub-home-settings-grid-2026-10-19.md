# RikkaHub Settings home: launcher hierarchy + Facets (2026-10-19)

## Correction

The earlier Settings redesign was still wrong: it grouped Models and
Agents & Assistants under **AI & Models**, inserting an unnecessary extra
navigation level despite the explicit request for TWO destinations on
the MAIN Settings menu. It also replaced the original grid with a vertical
list, even though the user's objection was to hidden horizontal navigation
and oversized padding, not to grids.

## Current implementation

- Main Settings screen has **seven direct, visible launcher shortcuts**:
  **Models**, **Facets**, Experience, Knowledge & Tools,
  Automation & Device, Privacy & Safety, Data & Maintenance.
- **Models** opens the existing unified ModelsPage directly.
- **Facets** (the chosen user-facing name for configured AI identities)
  opens the existing hub with Default Facet and Your Facets; the latter
  opens the list of configured identities, individual editors and overrides.
- The remaining five launchers act like folders: tapping one opens its
  destinations as a grid; Android back returns to the Settings home grid.
- All grid rows have compact icon squares and labels underneath, not
  full-size card backgrounds, with 3 columns on phones, 2 at narrow
  widths and 4 at wider widths. No horizontal chips or sideways scrolling.
- Search continues to search direct destinations and grouped destinations.
- Explicit core English user-facing labels (29 existing string resources)
  adopt **Facet/Facets**. Internal Kotlin data entities/routes remain
  Assistant for compatibility. These resource changes do not claim every
  locale translation or every generic use of 'assistant' has been rebranded;
  conversational role and protocol references remain semantically distinct.
- No data migration, provider changes, model changes, or bubble restoration.

## Verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: BUILD SUCCESSFUL;
  app JVM tests passed.
- SHA-256-verified private backup of database/datastore/shared preferences
  prior to installation, then in-place Pixel 10 Pro APK installation; no
  user data cleared.
- Real Pixel screenshots and UI accessibility hierarchy verified seven
  launchers on Settings home (Models, Facets + 5 folders), compact icon
  grid/labels, no AI & Models item.
- **Direct** Facets tap opens Default Facet / Your Facets. Experience folder
  opens a grid with five destinations; Android back returns to home.
- **Direct** Models tap opens the existing unified model manager with
  category filters and models; no intervening AI & Models submenu.
- No push. Public repository branch has unpublished historical data.


## Models launcher icon correction (2026-10-19)

The Models shortcut previously used `HugeIcons.Deepseek`, a vendor-specific
logo, even though the screen manages models from many providers. It now uses
`HugeIcons.Layers`, mapped to the existing MingCute `mgc_layers_line`
asset, depicting a generic layered collection. No icon assets were
downloaded or generated. The destination remains the unified ModelsPage.

Verified: successful app JVM suite and debug APK build, in-place Pixel
installation after SHA-256-verified private app backup, and inspected real
Settings-home screenshot showing the generic layers icon. No push.
