# PR6 Unified Models Design

## Goal

Replace the current `Screen.SettingModels` destination with one registry-backed
Models page. The route remains stable, but it must render `UnifiedModelsPage`
instead of the legacy assignment page. There must be no second user-facing
assignment destination.

## Scope

PR6 includes:

- `UnifiedModelsPage` and its view model.
- Registry-backed local and cloud inventory.
- Capability tabs for All, Chat, Vision, Image, Speech, and Embeddings.
- Collapsible cloud-provider groups and local-model groups.
- One `ModelAssignmentsSection` containing every persisted assignment.
- Registry-backed Chat, Vision, OCR, Image Generation, and Embeddings assignments.
- A compatibility adapter for editable Title and Translation assignments.
- Navigation from model/provider cards to existing lifecycle and configuration pages.
- Assignment-parity, filtering, disabled-model, and repair-state tests.

PR6 excludes provider-page rewrites, local-runtime rewrites, chat image tools,
universal artifact importing, and removal of legacy implementation files before
parity is proven.

## Architecture

Use the existing `app/.../data/modelregistry` package as the data seam. Do not
move or duplicate the registry models introduced by PR5. Add only the missing
compatibility adapter and UI-facing view-model behavior required by this page.

### Page

`UnifiedModelsPage` owns presentation state through `UnifiedModelsViewModel`.
The view model collects `ModelRegistry.models`, `.providers`, and
`.assignments`, then exposes:

- the selected `ModelTab`;
- search text;
- selected provider/source filters;
- the current assignment and repair state;
- navigation intents for existing provider/runtime detail pages.

The default `ModelsPageRequest` is All + no focus. Optional initial tab, focus,
provider ID, and model ID are page state, not new destinations. The existing
`Screen.SettingModels` route continues to construct the default request.

### Assignment surface

Extract assignment controls from `SettingModelPage.kt` into:

```kotlin
@Composable
fun ModelAssignmentsSection(
    assignments: ModelAssignments,
    availableModels: List<ModelDescriptor>,
    onAssignmentChanged: (ModelRole, String?) -> Unit,
)
```

The section has one visual surface with grouped rows:

- Conversation: Chat, Vision, OCR.
- Media: Image generation.
- Knowledge: Embeddings.
- Utility models: Title generation, Translation.

Vision and OCR remain separate roles even when one model advertises both
capabilities. OCR resolution may explicitly use a vision-capable fallback only
when the resolver policy requests it; the UI never merges the two selectors.

Title and Translation use `LegacyModelAssignmentAdapter` until formal registry
roles exist. The adapter reads and writes the existing persisted settings and
preserves null/clear semantics. No old assignment is silently discarded.

Selectors show only enabled compatible models. A selected model that becomes
disabled, unavailable, or incompatible produces a visible repair state; the
view model does not silently select a replacement.

### Inventory surface

Capability tabs filter the same registry inventory rather than maintaining
separate lists. Each model card shows:

- display name and provider/source;
- local/cloud and lifecycle state;
- capability badges, distinguishing unverified capabilities;
- enabled state;
- assignment roles using the model;
- a detail/configuration action.

Cloud providers render as collapsible groups with enablement, connection
status, catalog refresh, and Provider Settings actions. Local models render
with enable, verify/configure-runtime, and remove actions. Lifecycle mutations
remain owned by existing provider/local-management flows; the unified page
only navigates to those flows and refreshes registry state afterward.

## Data Flow

1. `Screen.SettingModels` opens `UnifiedModelsPage` with the default request.
2. The view model collects registry state and the legacy assignment adapter.
3. Search and capability/source/provider filters derive visible descriptors.
4. Assignment selectors call the registry or compatibility adapter after local
   validation; failures remain visible to the user.
5. Lifecycle/detail actions navigate to existing pages and trigger a registry
   refresh when returning.
6. Resolver repair state is retained until the user assigns a compatible model
   or explicitly clears the assignment.

No second model database, provider serialization, credential path, or local
installation path is introduced.

## Navigation

Keep `Screen.SettingModels` as the compatibility seam. Existing callers and
deep links continue to reach the unified page. `ModelsPageRequest` supports
initial tab, focus, provider ID, and model ID without multiplying destinations;
the current route supplies the default request, while future callers can pass a
request through the repository's existing navigation-state mechanism.

Existing provider detail, LiteRT, Stable Diffusion, and model-manager pages
become detail/configuration destinations. They are not linked as competing
assignment pages.

## Error Handling

- Unsupported assignment persistence returns an explicit UI error.
- A missing or disabled selected model creates a repair state, never an
  automatic fallback.
- Cloud fallback remains opt-in through the existing resolver policy.
- Registry refresh failures preserve the last usable inventory and expose an
  error state rather than clearing the page.
- Lifecycle actions are delegated; the page does not claim success until the
  delegated flow reports completion and the registry refreshes.

## Testing

Add pure JVM tests for:

- legacy title/translation adapter read, write, and clear behavior;
- assignment parity for all existing persisted defaults;
- distinct Vision and OCR selection and compatibility filtering;
- capability-tab and search filtering;
- disabled/unavailable selected-model repair states;
- provider grouping and local/cloud source separation;
- preservation of provider IDs, remote model IDs, and existing assistant model
  IDs.

Run focused model/registry tests, `./gradlew test assembleDebug --no-daemon`,
`:app:compileDebugKotlin --no-daemon`, and `git diff --check`.

## Exit Criteria

1. `Screen.SettingModels` opens only `UnifiedModelsPage`.
2. Existing assignments resolve to the same model IDs.
3. Title and Translation remain editable.
4. Chat, Vision, OCR, Image, and Embeddings selectors show only compatible
   enabled models.
5. Local and cloud inventory comes from `ModelRegistry`.
6. Provider groups are collapsible.
7. Existing lifecycle/configuration pages remain reachable from cards.
8. No second user-facing assignment page exists.
9. Removing or disabling a selected model produces a repair state.
10. Existing chats and assistant configurations continue resolving model IDs.
