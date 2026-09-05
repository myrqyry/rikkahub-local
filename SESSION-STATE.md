# Session State

## Current goal
Implement the agreed Models-page redesign on branch `aster/models-page-density` without disturbing working model/provider lifecycle behavior.

## Canonical UI decisions
- Models page answers: what models do I have, and what default jobs are they doing?
- Compact top app bar; search is an icon that expands only when used.
- Capability filters are a horizontal `LazyRow`: All, Chat, Vision, Image, Audio, Embeddings.
- `Retrieval` is not a primary top-level filter; reranking/retrieval remain model capabilities/metadata.
- Defaults are a compact status section (role/icon + selected model). Tapping the section opens the assignment page; it is not an inline editor.
- Inventory is grouped under collapsible provider headers. Provider status and provider-settings affordance live in the header. Tapping a model opens Model Details.
- Avoid heavy/nested cards and unnecessary containers; keep model rows dense.
- Remove the standalone Sources dump from the bottom of the Models page.
- Top-right `+` owns acquisition: add/configure a provider API, import a local model from filesystem, or import/download from Hugging Face. Do not add another bottom `Add Source` action.
- Recommended-model discovery is separate by capability/type and contextual; the inventory page must not become a marketplace.

## Verified implementation state before edits
- `ModelsPage.kt` still uses `LargeTopAppBar`, permanent search field, static `Row` of filter chips, heavy `CardGroup` defaults, inline expanded assignments, flat inventory, and a bottom Sources section.
- `ModelsFilter.kt` currently exposes `RETRIEVAL` for embeddings/reranking.
- `ModelInventorySection.kt` currently renders one flat `CardGroup` of models.
- `AddToModelsSheet.kt` currently mixes local catalog discovery with provider connection; it will need a later separation pass.
- Existing `Screen.SettingDefaultModels` routes to `ModelsPage(showAssignments = true)` and can be reused as the dedicated assignments destination with a focused layout.

## Next actions
1. Refactor `ModelsPage.kt` shell/search/filter/defaults and make assignment mode focused.
2. Refactor `ModelInventorySection.kt` into provider-grouped collapsible inventory while preserving model toggle/detail behavior.
3. Rename the top-level Retrieval filter to Embeddings.
4. Separate acquisition (`+`) from recommended-model discovery.
5. Run/inspect available CI/build validation and report anything not executable from this environment.
