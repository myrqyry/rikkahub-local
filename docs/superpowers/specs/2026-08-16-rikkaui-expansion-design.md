# RikkaUI Expansion Design

> Status: Approved. Scope: Emit + interact (form input round-trip). Roadmap Phase G.

## 1. Background and goal

Generated native UI is currently a "small component set" (architecture.md: partial).
`UIMessagePart.GeneratedUi` exists and renders through `RikkaUiRenderer`, but the
schema has only seven static read-only nodes (`Column`, `Text`, `Button`, `Chip`,
`Image`, `List`, `Divider`) and two actions (`Copy`, `OpenUrl`). There is **no
producer** — no tool, executor, or agent code emits a `RikkaUi` tree in main source
(the only construction is `RikkaUiSerializationTest`). This phase expands the
schema with layout, form, and input primitives, adds an emit tool, and completes
the input round-trip as a typed user event.

Principles (from architecture.md):
- Generated UI is typed `UIMessagePart.GeneratedUi`, never JSON-through-text.
- Compose is the primary renderer; the generated UI is the human projection of
  agent state; semantic agent state stays the machine projection.
- Keep the primitive set narrow and renderable; no arbitrary styling knobs.

## 2. Schema additions (ai module, `RikkaUi.kt`)

New `RikkaUi` node types (each `@Serializable` + `@SerialName`):

| Node | Fields | SerialName |
|------|--------|------------|
| `Form` | `id: String`, `children: List<RikkaUi>`, `spacing: Int = 8`, `verticalAlignment: String = "top"` | `ui_form` |
| `Row` | `children: List<RikkaUi>`, `spacing: Int = 8`, `verticalAlignment: String = "center"` (`"top"`\|`"center"`\|`"bottom"`) | `ui_row` |
| `Input` | `key: String`, `placeholder: String? = null`, `label: String? = null`, `initial: String? = null` | `ui_input` |
| `Toggle` | `key: String`, `label: String`, `initial: Boolean = false` | `ui_toggle` |
| `Select` | `key: String`, `label: String`, `options: List<String>`, `initial: String? = null` | `ui_select` |
| `Progress` | `fraction: Float? = null` | `ui_progress` |
| `Link` | `label: String`, `url: String` | `ui_link` |

New `RikkaUiAction` variants (besides existing `Copy`, `OpenUrl`):

| Action | Fields | SerialName |
|--------|--------|------------|
| `Submit` | `formId: String` | `ui_submit` |
| `Navigate` | `destination: String` | `ui_navigate` |

Backward-compatible axis fix: existing `Column.verticalAlignment` is misnamed — it
controls **horizontal** alignment of children in the column. Compose `Row`s arrange
children horizontally and align them **vertically**; the new `Row.verticalAlignment`
must mean vertical alignment. Give `Column` a custom serializer that accepts both
the legacy key and the new semantics so old payloads deserialize unchanged; do not
repeat the misnaming in `Row`.

Validator (pure function, JVM-testable):
- depth ≤ 6; node count ≤ 100; `spacing` in 0..32.
- exactly one `Form` per tree; `Form.id` non-empty; `Form.id` and every input
  `key` unique across the tree.
- `Select`: `options` non-empty and distinct; `initial` (when present) ∈ options.
- `Progress.fraction` is `null` or in 0f..1f.
- URL scheme ∈ {`http`, `https`, `file`, `content`} (for `Link.url`, `Image.url`,
  and `OpenUrl.url`).
- `Navigate.destination` must be in a fixed allowlist (passed in), never a raw
  model-authored route.

## 3. Emit tool: `render_ui` (ai module)

A single `render_ui` tool owns emission. No separate form submission tool.
- `parameters()` mirrors the RikkaUi schema as a strict typed JSON input schema.
- `execute(json)` parses + validates; on success returns
  `[Text("""{"ok":true,"rendered":true}""")]` — the **model-facing receipt only**.
  `GeneratedUi` is **not** placed in `Tool.output`.
- On invalid input, returns a `Text` error envelope describing the first violation
  (same shape as other tools' failures).
- Pure JVM: parse + validate are deterministic and unit-tested without the renderer.

## 4. Compose state expression (app module, `RikkaUiRenderer`)

New signature:

```kotlin
@Composable
fun RikkaUiRenderer(
    ui: RikkaUi,
    renderId: String,
    onSubmit: (RikkaUiEvent.FormSubmit) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

State:
- One root state map `Map<String, String>` per renderer instance, keyed by
  `renderId` (the originating tool call id).
- Backed by `rememberSaveable(renderId, saver = formValuesSaver) { mutableStateOf(seedFrom(ui)) }`
  — `renderId` is an input to `rememberSaveable` directly, not nested inside
  `remember`. Explicit saver for `Map<String, String>`. LazyColumn already keys
  message items by stable `MessageNode.id`, so saveable machinery gets a stable
  parent identity.
- Seeded from every field's `initial`: `Input` → `initial`; `Toggle` → `initial`
  as `"true"`/`"false"`; `Select` → `initial`.
- Input changes update local root state **per keystroke**; no chat/model event is
  emitted until **Submit**. (Debounce only guards double-taps on Submit, if at all.)
- Unselected `Select` value is present as `""` (empty string) in the submitted map.
- Untouched `Input` submits its `initial`; untouched `Toggle` submits its `initial`.

Submit flow: `Submit(formId)` action snapshots the root map and calls
`onSubmit(RikkaUiEvent.FormSubmit(renderId, formId, values))`.

`Navigate(destination)` calls `onNavigate(destination)`; the app-side handler
routes through the fixed allowlist only.

New renderer cases: `Form` → scoped sub-column bound to `formId`; `Row` → Compose
`Row` with vertical child alignment; `Input` → `OutlinedTextField` (single line);
`Toggle` → `Switch` with row label; `Select` → `ExposedDropdownMenuBox`;
`Progress` → `LinearProgressIndicator` (indeterminate when `fraction == null`);
`Link` → clickable `Text`/`TextButton` opening the URL. Existing seven nodes keep
their current behavior.

## 5. Lift seam + identity chain (app module, `GenerationHandler`)

Identity chain: `Tool.toolCallId` → `GeneratedUi.renderId` → `RikkaUiRenderer(renderId)`
→ `RikkaUiEvent.FormSubmit.renderId`.

`GeneratedUi` evolves to carry `renderId`:

```kotlin
@Serializable
@SerialName("generated_ui")
data class GeneratedUi(
    val renderId: String,
    val ui: RikkaUi,
    override var metadata: JsonObject? = null,
) : UIMessagePart()
```

Lift rule (post-execution seam in `GenerationHandler`, where `messages` is rebuilt
with updated Tool parts):
- Only lift when the executed `render_ui` tool carries the **success receipt**
  (`{"ok":true,"rendered":true}`). `isExecuted` alone is insufficient — failures
  also produce a non-empty `Text` error envelope.
- Re-parse and re-validate the tool's input (`inputAsJson`) to derive `ui`.
- Do **not** interleave `GeneratedUi` between consecutive Tool parts — RikkaHub
  groups consecutive executed Tool parts so provider adapters preserve
  tool-call/result boundaries. Append all lifted `GeneratedUi(renderId, ui)` parts
  **after** the Tool block (same message, sibling parts).
- Idempotent: refuse to add a second `GeneratedUi` with the same `renderId`.
- Tool output stays the model-facing receipt; `GeneratedUi` stays app-facing state.

Verified provider behavior: OpenAI/Claude/Google adapters carry only `Text`/`Image`
out of ordinary content and handle `Tool.output` separately; unsupported part types
yield `null`. `GeneratedUi` inside `Tool.output` is invisible to model and top-level
renderer today — the lift is required for both.

## 6. Input round-trip: typed user event (app module)

Keep `FormSubmit` typed through the UI; convert to text at chat ingress (ChatVM),
not inside providers.

```kotlin
sealed interface RikkaUiEvent {
    data class FormSubmit(
        val renderId: String,
        val formId: String,
        val values: Map<String, String>,
    ) : RikkaUiEvent
}
```

Flow: `RikkaUiRenderer` → `RikkaUiEvent.FormSubmit` → `ChatPage`/`ChatVM` →
deterministic `FormSubmit`→model-text conversion → `UIMessagePart.Text` → ordinary
new **user** turn via `ChatVM.handleMessageSend(List<UIMessagePart>)` →
`ChatService.sendMessage`.

Canonical model-facing text (values keys sorted before encoding for determinism):

```json
{"type":"rikka_ui_form_submit","renderId":"call_123","formId":"settings","values":{"enabled":"true","name":"Mat","mode":""}}
```

Do **not** put a typed `FormSubmit` `UIMessagePart` through persistence/provider
serialization — it becomes text immediately; adding a persisted type would force
`isEmptyInputMessage()`, every provider adapter, rendering, and migration changes
for a type that never survives to the model anyway.

Naming: the rendering path is `ChatPage` / `ChatPageContent` → `ChatList` →
`ChatMessage` → `MessagePartsBlock` → `RikkaUiRenderer`. `ChatMessage` already has
the `is UIMessagePart.GeneratedUi -> RikkaUiRenderer(ui)` case; this extends that
existing seam (adds `renderId` + callbacks), it does not create a new rendering
channel.

## 7. Testing

ai module (JVM):
- Validator tests: depth/node-count bounds, spacing bounds, one-Form rule,
  duplicate key/`formId` rejection, `Select` options empty/duplicate/initial∉,
  `Progress` fraction bounds, unsafe URL schemes, non-allowlisted `Navigate`.
- Serialization round-trip for every new node + action; legacy `Column` payload
  deserializes with the axis fix applied.
- `render_ui` execute: valid tree → receipt `{"ok":true,"rendered":true}`; invalid
  tree → error envelope; `GeneratedUi` absent from tool output.

app module (JVM seams):
- Lift seam: (a) multiple contiguous tools stay contiguous after lifting;
  (b) multiple `render_ui` calls get distinct `renderIds`; (c) re-running lift is
  idempotent; (d) a failed `render_ui` receipt never creates `GeneratedUi`.
- FormSubmit conversion: untouched `Input` gets `initial` `?: ""`; `Toggle` gets
  `"true"`/`"false"`; unselected `Select` present as `""`; values keys
  deterministically ordered; `renderId`/`formId` survive intact.
- Renderer: existing `ChatMessage` rendering tests keep passing; new-node render
  smoke checks (Compose UI test where feasible).

No device test required this phase (renderer is Compose-level; existing message
rendering suite covers the seam).

## 8. Files

ai module:
- Modify `ai/src/main/java/me/rerere/ai/ui/RikkaUi.kt` (new nodes + actions +
  `Column` axis-fix serializer + validator).
- Create `ai/src/main/java/me/rerere/ai/ui/RikkaUiValidator.kt` (pure validator).
- Create `ai/src/main/java/me/rerere/ai/tools/RenderUiTool.kt` (`render_ui` tool).
- Modify `ai/src/main/java/me/rerere/ai/ui/Message.kt` (`GeneratedUi` + `renderId`).
- Tests in ai.

app module:
- Modify `app/src/main/java/me/rerere/rikkahub/ui/components/message/RikkaUiRenderer.kt`
  (new signature + state + new-node rendering).
- Create `app/src/main/java/me/rerere/rikkahub/ui/components/message/RikkaUiEvent.kt`
  (`RikkaUiEvent.FormSubmit` + saver).
- Modify `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
  (lift seam: append `GeneratedUi` after Tool block, idempotent by `renderId`).
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt` (ingress:
  `FormSubmit` → sorted-keys JSON text → `handleMessageSend`).
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` +
  `ChatMessage.kt` (pass `renderId` + `onSubmit`/`onNavigate` through the existing
  seam; navigation allowlist handler).
- Tests in app (JVM lift + conversion; compose smoke).

## 9. Out of scope (this phase)

- Standalone form-submission tool (rejected: provider protocols require
  tool-call/result pairing; submission is a user event).
- Persisting a typed `FormSubmit` `UIMessagePart` (rejected: becomes text at
  ingress).
- Arbitrary styling knobs, drag/resize, complex containers beyond `Form`/`Row`.
- `RikkaUiEvent` variants beyond `FormSubmit` (future: e.g. selection confirm).
- RikkaUI in Telegram/web surfaces (app Compose only, per roadmap).
