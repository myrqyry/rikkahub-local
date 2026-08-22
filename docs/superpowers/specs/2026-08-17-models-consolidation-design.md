# Models consolidation

## Goal

Replace three separate Settings destinations — **Model Manager**, **Default
Models**, and **Providers** — with one top-level **Models** destination.
Organize the surface around the object the user actually manages (the model),
not around whichever subsystem got its own Kotlin package first.

Local/cloud becomes metadata (a source badge) instead of the primary
navigation structure. "Provider" stays an internal term; the UI says
**Sources** because it naturally includes local models ("On this device").

This supersedes `2026-08-12-model-manager-unification-design.md` (Model
Manager). It is a **UI/product consolidation first** — the registry,
providers, local runtime inventory, role assignments, and provider-detail
implementations are preserved, not rewritten.

## User experience

### Settings home (AI section)

The `aiModels` section items `modelManager`, `defaultModels`, and `providers`
are replaced by a single **Models** entry. The section keeps Agent, Assistants,
Prompt Library, and Translate Bubble unchanged.

### Models page

```
‹  Models                                      ＋

Search models...

[ All ] [ Chat ] [ Vision ] [ Image ] [ Audio ] [ Retrieval ]

USED BY DEFAULT
Chat                 Gemma 4 • On device        >
Vision               Gemini 2.5 Flash • Google  >
Image generation     SD 2.1 Turbo • On device   >
Embeddings           ...                        >
                    Show all assignments

YOUR MODELS
Gemma 4 E2B         On device    Chat • Vision      ● Ready      [ON]
Gemini 2.5 Flash    Google       Chat • Vision • OCR ● Connected [ON]
SD 2.1 Turbo Q8     On device    Image generation    ● Ready      [ON]
GPT-5               OpenAI       Chat • Vision       ● Connected [OFF]

SOURCES
On this device    4 models                              >
Google            Configured • 12 models               >
OpenAI            Disabled • 8 models                  >
OpenRouter        Not configured                       >
                    Manage sources                      >
```

Page structure (single `LazyColumn`):

1. **Search field + capability filter chips.**
2. **Used by default** — compact rows of current assignments
   (`defaultAssignmentsSummary`), collapsed by default. **Show all
   assignments** expands the full existing `ModelAssignmentsSection` inline.
3. **Your models** — flattened inventory, one consistent card per model:
   display name, source badge, capability chips, status, master enable
   `Switch`. Tap → `ModelDetail`.
4. **Sources** — summary rows: **On this device** (N installed) then one row
   per provider (Configured/Disabled • N models, or Not configured). Tap a
   provider row → `SettingProviderDetail`. Tapping **On this device** filters
   Your models to local-only. **Manage sources** opens the full-height
   `ManageSourcesSheet`.

**Search suppresses the furniture.** With a blank query, the page shows Used
by default → Your models → Sources. Once the user types, Used by default and
Sources are hidden and matching models render directly under the search field.
Filters affect only Your models; Sources and assignments stay conceptually
independent.

### Model detail page

Same shell for every model, source-specific details where appropriate:

```
Gemini 2.5 Flash

Source         Google                        >
Capabilities
   Chat [on]     Vision [on]     OCR [off]
Used for
   Chat   Vision   OCR
Status
   Enabled   Available
Source details
   Source         Google
   Remote ID      gemini-2.5-flash
   Connection     Healthy
   Provider settings  >
```

- **Capabilities** — one `Switch` per capability the model actually supports,
  each toggling `registry.setCapabilityEnabled` (new thin VM wrapper
  `setCapabilityEnabled`). This gives a meaningful UI to the capability-level
  enablement architecture. Only supported capabilities are listed, so an "off"
  switch always means disabled, never unsupported.
- **Used for** — the roles that currently reference this model.
- **Status** — overall enablement + readiness (Ready / Connected / Available).
- **Local details** — Storage (from new `sizeBytes` metadata), Runtime,
  Location (file path), **Rename**, **Delete** (moved here from the inventory).
- **Cloud details** — Source, Remote ID, Connection, **Provider settings ›**
  (→ `SettingProviderDetail`).
- **Advanced** — Model ID (context window / custom parameters only where data
  already exists; most of that lives in the provider settings page, so this
  stays a shell for now).

### Add sheet (top `＋`)

`AddToModelsSheet`, a `ModalBottomSheet` with two groups, reusing existing
flows:

- **On device**: Import model file · Download from URL · Browse supported
  models — reuses `ModelManagerViewModel`'s catalog / HF-URL / file-import UI
  verbatim.
- **Connect a source**: recommended providers + custom endpoint — reuses the
  `RECOMMENDED_PROVIDERS` sheet and `ProviderConfigure` dialog currently in
  `SettingProviderPage`.

### Manage sources sheet

`ManageSourcesSheet`, full-height, opened by **Manage sources** in the Sources
heading — **not** inline in the main list (avoids two searches and reorder
gestures inside a heterogeneous `LazyColumn`). Contains the provider
list-management UI extracted from `SettingProviderPage`: search, drag
reorder, long-press delete, and navigation to `SettingProviderDetail`. No
Settings entry points to this; it only lives inside Models.

## Taxonomy

New capability-based user filters, not a 1:1 projection of `ModelTab`:

| Filter | Matches capabilities |
|--------|----------------------|
| All | everything |
| Chat | CHAT |
| Vision | VISION, OCR, DOCUMENT_ANALYSIS |
| Image | IMAGE_GENERATION, IMAGE_EDITING |
| Audio | TEXT_TO_SPEECH, SPEECH_TO_TEXT, AUDIO_UNDERSTANDING |
| Retrieval | EMBEDDINGS, RERANKING |

Pure functions in `ModelsFilter.kt`:

- `enum class ModelsFilter { ALL, CHAT, VISION, IMAGE, AUDIO, RETRIEVAL }`
- `ModelsFilter.matches(model: ModelDescriptor): Boolean`
- `ModelTab.toModelsFilter(): ModelsFilter` — backward mapping so
  `ModelManagerRequest` (which keeps accepting the old enum) maps into the
  nearest UI filter: `ALL→ALL`, `CHAT→CHAT`, `VISION→VISION`, `IMAGE→IMAGE`,
  `SPEECH→AUDIO`, `EMBEDDINGS→RETRIEVAL`, `TASK→VISION` (2 of 3 capabilities
  live there), `OTHER→ALL`.
- `searchMatches(model, query)` for the shared display-name/id predicate.

This describes what humans think the model does, not the historical enum
structure.

## Data and compatibility

- `SettingsModelRegistry`, `ModelDescriptor`, `ModelCapability`, `ModelRole`,
  `ModelAssignments`, `ModelProviderDescriptor` remain authoritative.
- **Small, safe registry change:** `SettingsModelRegistry.descriptor()` adds
  `metadata["path"]` and `metadata["sizeBytes"]` for local models
  (`File(path).length()` at build time — no IO at render). Inventory-local
  descriptors already carry `path`; provider-backed locals gain it here.
- `UnifiedModelsViewModel` gains `setCapabilityEnabled(modelId, capability,
  enabled)` wrapping the existing `registry.setCapabilityEnabled`. All other
  VM behavior is untouched.
- Provider visibility and per-model capability enablement keep persisting in
  settings. Provider refresh / enable / reorder / delete flows are reused.
- **State wording:** Source rows say **Configured** / **Disabled** / **Not
  configured**, never "Connected" — `ModelProviderDescriptor` only exposes
  `id`, `displayName`, `enabled`, `modelIds`, so provider-level live
  connectivity cannot be asserted honestly. "Connected" can arrive later with
  a provider health/preflight feature without changing the layout.
  Per-descriptor `connected` is used only on the Model Detail page, where the
  data exists.

## Routes and deep links

New routes:

- `Screen.Models(request: ModelManagerRequest = ModelManagerRequest(),
  showAssignments: Boolean = false, scrollToSources: Boolean = false)`
- `Screen.ModelDetail(modelId: String)`

`SettingProviderDetail(providerId)` is kept unchanged.

**Compatibility aliases (do NOT delete old route types in this migration).**
Android may restore persisted route state across an app update; a deleted
route is not cheap insurance. Keep deprecated entries that render the Models
page with an anchor, and remove them once compatibility is proven:

- `SettingModelManager(request)` → `Models(request)`
- `SettingDefaultModels` → `Models(showAssignments = true)`
- `SettingProvider` → `Models(scrollToSources = true)`

No Settings entries point to them and no new code uses them.

Live call sites repointed to `Screen.Models` / `Screen.ModelDetail`:

- `SettingPage.kt` (three entries → one Models entry; `ProviderConfigWarningCard`
  → Models)
- `ImgGenPage.kt` (:394, :513), `AssistantBasicPage.kt` (:175),
  `ModelList.kt` (:709), `ProviderConfigure.kt` (:1395) — anchor-preserving
  via `ModelManagerRequest`
- `ErrorCard.kt` (:168, Default Models) → `Models(showAssignments = true)`
- `DoctorScreen.kt` / `DoctorChecks.kt` / `DoctorModels.kt`
  (`AppRouteKey.SettingProvider` → Models)

## File plan

New:

- `ui/pages/models/ModelsPage.kt`
- `ui/pages/models/ModelDetailPage.kt`
- `ui/pages/models/ModelsFilter.kt` (taxonomy + pure predicates)
- `ui/pages/models/DefaultAssignmentsSummary.kt` (pure)
- `ui/pages/models/components/AddToModelsSheet.kt`
- `ui/pages/models/components/ManageSourcesSheet.kt`
- `ui/pages/models/components/SourceBadge.kt` (badge + status label helpers)

Deleted (functionality absorbed):

- `ui/pages/modelmanager/ModelManagerPage.kt`
- `ui/pages/models/DefaultModelsPage.kt`
- `ui/pages/setting/SettingProviderPage.kt`

Kept and reused:

- `ModelManagerViewModel` (download/catalog/import machinery)
- `ModelAssignmentsSection`, `SettingProviderDetailPage`
- `UnifiedModelsViewModel` (+ one new thin method)

Modified:

- `ModelInventorySection` → flattened card presentation (local/cloud split
  removed; rename/delete moved to Model Detail; provider configure/refresh/
  enable moves to Sources/ManageSourcesSheet)
- `SettingPage.kt`, `RouteActivity.kt`, `Screen` sealed class,
  `SettingsModelRegistry` (sizeBytes/path), `strings.xml`

## Strings and i18n

New `strings.xml` keys: Models, Your models, Used by default, Show all
assignments, Sources, Manage sources, Add to Models, On device, Connect a
source, On this device, Configured / Disabled / Not configured, Capabilities,
Used for, Source details, Storage, Runtime, Location, Remote ID, Provider
settings, the six filter labels. Reuse existing `unified_models_*`,
`local_llm_*`, and `setting_provider_*` keys where possible. Translations flow
through locale-tui afterward (not part of this change).

## Scope exclusions

- No data-layer rewrite; no second model catalog/downloader.
- Do not remove Chinese/custom providers or provider network behavior.
- Do not remove persisted default-model fields or the assignment machinery.
- Do not change generation, speech, or provider runtime behavior beyond
  classification/visibility and the `sizeBytes`/`path` metadata.
- No provider health/preflight ("Connected") — future.
- No dedicated On-device source detail page beyond the local-only filter —
  accelerator/runtime configuration stays at the source level and is deferred.

## Acceptance criteria

- Settings shows one **Models** entry instead of Model Manager, Default
  Models, and Providers.
- Models page renders Used by default (collapsed, expandable) → Your models →
  Sources; searching hides Used by default and Sources.
- Capability chips (All/Chat/Vision/Image/Audio/Retrieval) filter Your models
  by capability; old `ModelTab` anchors map to the nearest filter.
- Every model renders as one consistent card with a source badge; local/cloud
  is metadata, not structure.
- `＋` opens Add to Models with On device + Connect a source groups.
- Manage sources opens the full-height sheet (reorder/delete/search); tapping
  a provider source opens `SettingProviderDetail`.
- Model Detail shows per-capability switches, Used for, and
  source-specific details; local rename/delete live here.
- Old route types remain as compatibility aliases; no new code targets them.
- Source rows use Configured/Disabled/Not configured, never Connected.
- `UnifiedModelsViewModelTest` and `ModelAssignmentsSectionTest` stay green;
  new pure-function tests cover `defaultAssignmentsSummary`,
  `ModelsFilter.matches`, `ModelTab.toModelsFilter`, and source badge/status
  labels.

## Verification gate

`./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`, then
`./gradlew lint`. App data is preserved (no destructive operations; install
APKs with `adb install -r`).
