# RikkaUI Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a model emit interactive RikkaUI trees (forms/inputs/selects/toggles/progress/rows/links) via a `render_ui` tool, lift them into the assistant message as `GeneratedUi` siblings, and round-trip user form submissions back into the conversation as typed user events.

**Architecture:** The ai module owns the schema (`RikkaUi`), a pure validator, and the `render_ui` tool (model-facing receipt only). The app module renders via `RikkaUiRenderer` (one root state map keyed by `renderId`), lifts successful render_ui output into the message (`GenerationHandler` seam), and converts `RikkaUiEvent.FormSubmit` to canonical text at chat ingress.

**Tech Stack:** Kotlin, kotlinx.serialization, Jetpack Compose, Room-era persistence untouched.

## Global Constraints

- Pure, deterministic, JVM-testable seams live in the **ai module** and never depend on app.
- `GeneratedUi` carries `renderId`; identity chain: `Tool.toolCallId` → `GeneratedUi.renderId` → `RikkaUiRenderer(renderId)` → `RikkaUiEvent.FormSubmit.renderId`.
- `render_ui` success returns `[Text("{\"ok\":true,\"rendered\":true}")]` only — `GeneratedUi` NEVER appears in `Tool.output`.
- Lift ONLY on success receipt `{"ok":true,"rendered":true}`; append lifted parts AFTER the Tool block; never interleave between consecutive Tools; idempotent by renderId.
- FormSubmit becomes `UIMessagePart.Text` at chat ingress (never persisted as a typed part). Canonical text with SORTED value keys: `{"type":"rikka_ui_form_submit","renderId":"...","formId":"...","values":{...}}`.
- Validator: depth≤6, node count≤100, spacing 0..32, exactly one Form per tree, unique Form.id + keys, Select options nonempty+distinct+initial∈options, Progress.fraction null or 0f..1f, URL scheme∈{http,https,file,content}, Navigate.destination∈allowlist.
- `Column.verticalAlignment` is legacy-misnamed (controls horizontal alignment); new `Row.verticalAlignment` aligns children vertically. Backward-compatible deserialization required.
- ai module runs pure JVM junit + runBlocking (NO kotlinx-coroutines-test). No device test this phase.
- docs/superpowers/ is gitignored — commit plan/spec with `git add -f`.

---

### Task 1: Extend the RikkaUi schema

**Files:**
- Modify: `ai/src/main/java/me/rerere/ai/ui/RikkaUi.kt`
- Test: `ai/src/test/java/me/rerere/ai/ui/RikkaUiSerializationTest.kt`

**Interfaces:**
- Produces: new `RikkaUi` nodes `Form`, `Row`, `Input`, `Toggle`, `Select`, `Progress`, `Link`; new `RikkaUiAction` variants `Submit(formId)`, `Navigate(destination)`. `Column` gains a backward-compatible custom serializer.

- [ ] **Step 1: Write the failing serialization tests**

Add to `RikkaUiSerializationTest.kt`:

```kotlin
@Test
fun `all new nodes and actions round trip`() {
    val tree: RikkaUi = RikkaUi.Form(
        id = "settings",
        children = listOf(
            RikkaUi.Row(children = listOf(RikkaUi.Text("a"), RikkaUi.Text("b")), spacing = 4),
            RikkaUi.Input(key = "name", placeholder = "Name", label = "Name", initial = "Mat"),
            RikkaUi.Toggle(key = "enabled", label = "Enabled", initial = true),
            RikkaUi.Select(key = "mode", label = "Mode", options = listOf("auto", "manual"), initial = "auto"),
            RikkaUi.Progress(fraction = 0.5f),
            RikkaUi.Link(label = "docs", url = "https://example.com"),
        ),
    )
    val json = Json.encodeToString(RikkaUi.serializer(), tree)
    assertEquals(tree, Json.decodeFromString(RikkaUi.serializer(), json))
}

@Test
fun `legacy column verticalAlignment still deserializes`() {
    val json = """{"type":"ui_column","children":[],"verticalAlignment":"bottom"}"""
    val col = Json.decodeFromString(RikkaUi.serializer(), json) as RikkaUi.Column
    assertEquals("bottom", col.verticalAlignment)
}

@Test
fun `new actions round trip`() {
    val json = Json.encodeToString(
        RikkaUiAction.serializer(),
        listOf<RikkaUiAction>(RikkaUiAction.Submit("f1"), RikkaUiAction.Navigate("/images")).last(),
    )
    assertTrue(json.contains("ui_navigate"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ai:testDebugUnitTest --tests "*RikkaUiSerializationTest*"`
Expected: FAIL — `Form`/`Row`/`Input`/`Submit`/`Navigate` unresolved.

- [ ] **Step 3: Implement the schema additions**

In `RikkaUi.kt` inside `sealed class RikkaUi` add:

```kotlin
@Serializable
@SerialName("ui_form")
data class Form(
    val id: String,
    val children: kotlin.collections.List<RikkaUi>,
    val spacing: Int = 8,
    val verticalAlignment: String = "top",
) : RikkaUi()

@Serializable
@SerialName("ui_row")
data class Row(
    val children: kotlin.collections.List<RikkaUi>,
    val spacing: Int = 8,
    val verticalAlignment: String = "center",
) : RikkaUi()

@Serializable
@SerialName("ui_input")
data class Input(
    val key: String,
    val placeholder: String? = null,
    val label: String? = null,
    val initial: String? = null,
) : RikkaUi()

@Serializable
@SerialName("ui_toggle")
data class Toggle(
    val key: String,
    val label: String,
    val initial: Boolean = false,
) : RikkaUi()

@Serializable
@SerialName("ui_select")
data class Select(
    val key: String,
    val label: String,
    val options: kotlin.collections.List<String>,
    val initial: String? = null,
) : RikkaUi()

@Serializable
@SerialName("ui_progress")
data class Progress(val fraction: Float? = null) : RikkaUi()

@Serializable
@SerialName("ui_link")
data class Link(val label: String, val url: String) : RikkaUi()
```

Inside `sealed class RikkaUiAction` add:

```kotlin
@Serializable
@SerialName("ui_submit")
data class Submit(val formId: String) : RikkaUiAction()

@Serializable
@SerialName("ui_navigate")
data class Navigate(val destination: String) : RikkaUiAction()
```

Backward-compatible `Column` deserialization: change `Column`'s annotation to a custom serializer that reads both the legacy key `"verticalAlignment"` and the corrected semantic (value unchanged, same string) — it already stores `"top"|"center"|"bottom"` and maps to horizontal alignment in the renderer, so the serializer accepts the same shape:

```kotlin
@Serializable(with = ColumnSerializer::class)
@SerialName("ui_column")
data class Column(
    val children: kotlin.collections.List<RikkaUi>,
    val spacing: Int = 8,
    val verticalAlignment: String = "top",
) : RikkaUi()
```

Add the serializer in the same file:

```kotlin
private object ColumnSerializer : KSerializer<Column> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ui_column") {
        element("children", ListSerializer(RikkaUi.serializer()).descriptor)
        element("spacing", Int.serializer().descriptor, isOptional = true)
        element("verticalAlignment", String.serializer().descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: Column) {
        val output = encoder.beginStructure(descriptor)
        output.encodeSerializableElement(descriptor, 0, ListSerializer(RikkaUi.serializer()), value.children)
        output.encodeIntElement(descriptor, 1, value.spacing)
        output.encodeStringElement(descriptor, 2, value.verticalAlignment)
        output.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Column {
        val input = decoder.beginStructure(descriptor)
        var children: List<RikkaUi> = emptyList()
        var spacing = 8
        var verticalAlignment = "top"
        loop@ while (true) {
            when (val index = input.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> children = input.decodeSerializableElement(descriptor, 0, ListSerializer(RikkaUi.serializer()))
                1 -> spacing = input.decodeIntElement(descriptor, 1)
                2 -> verticalAlignment = input.decodeStringElement(descriptor, 2)
                else -> throw SerializationException("Unknown index $index")
            }
        }
        input.endStructure(descriptor)
        return Column(children, spacing, verticalAlignment)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ai:testDebugUnitTest --tests "*RikkaUiSerializationTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai/src/main/java/me/rerere/ai/ui/RikkaUi.kt ai/src/test/java/me/rerere/ai/ui/RikkaUiSerializationTest.kt
git commit -m "feat: extend RikkaUi with form primitives"
```

---

### Task 2: Add the pure RikkaUi validator

**Files:**
- Create: `ai/src/main/java/me/rerere/ai/ui/RikkaUiValidator.kt`
- Test: `ai/src/test/java/me/rerere/ai/ui/RikkaUiValidatorTest.kt`

**Interfaces:**
- Consumes: `RikkaUi`, `RikkaUiAction` from Task 1.
- Produces: `fun validateRikkaUi(ui: RikkaUi): List<String>` returning error strings (empty = valid); `const DEFAULT_NAV_DESTINATIONS: Set<String>` allowlist.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `valid form passes`() {
    val tree = RikkaUi.Form("s", listOf(RikkaUi.Input("name"), RikkaUi.Toggle("enabled", "Enabled")))
    assertEquals(emptyList(), validateRikkaUi(tree))
}

@Test
fun `nested depth beyond six is rejected`() {
    var tree: RikkaUi = RikkaUi.Text("leaf")
    repeat(7) { tree = RikkaUi.Column(listOf(tree)) }
    assertTrue(validateRikkaUi(tree).any { it.contains("depth") })
}

@Test
fun `more than one form is rejected`() {
    val tree = RikkaUi.Form("a", listOf(RikkaUi.Form("b", emptyList())))
    assertTrue(validateRikkaUi(tree).any { it.contains("one") || it.contains("Form") })
}

@Test
fun `duplicate keys are rejected`() {
    val tree = RikkaUi.Form("s", listOf(RikkaUi.Input("k"), RikkaUi.Toggle("k", "x")))
    assertTrue(validateRikkaUi(tree).any { it.contains("unique") })
}

@Test
fun `select with empty or duplicated options rejected`() {
    assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", emptyList())).isNotEmpty())
    assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", listOf("a", "a"))).isNotEmpty())
}

@Test
fun `select initial must be in options`() {
    assertTrue(validateRikkaUi(RikkaUi.Select("s", "S", listOf("a"), "z")).isNotEmpty())
}

@Test
fun `progress fraction must be in range`() {
    assertTrue(validateRikkaUi(RikkaUi.Progress(1.5f)).isNotEmpty())
    assertEquals(emptyList(), validateRikkaUi(RikkaUi.Progress(null)))
    assertEquals(emptyList(), validateRikkaUi(RikkaUi.Progress(0.5f)))
}

@Test
fun `unsafe url scheme rejected`() {
    assertTrue(validateRikkaUi(RikkaUi.Link("x", "javascript:alert(1)")).isNotEmpty())
    assertTrue(validateRikkaUi(RikkaUi.Image("ftp://host/f.png")).isNotEmpty())
}

@Test
fun `navigate destination must be allowlisted`() {
    assertTrue(validateRikkaUi(RikkaUi.Button("x", RikkaUiAction.Navigate("/nonexistent"))).isNotEmpty())
    assertTrue(validateRikkaUi(RikkaUi.Button("x", RikkaUiAction.Navigate("/images"))).isEmpty())
}

@Test
fun `spacing out of range rejected`() {
    assertTrue(validateRikkaUi(RikkaUi.Column(emptyList(), spacing = 64)).isNotEmpty())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ai:testDebugUnitTest --tests "*RikkaUiValidatorTest*"`
Expected: FAIL — `validateRikkaUi` unresolved.

- [ ] **Step 3: Implement the validator**

```kotlin
package me.rerere.ai.ui

import java.net.URI

const val RIKKAUi_MAX_DEPTH = 6
const val RIKKAUi_MAX_NODES = 100

val DEFAULT_NAV_DESTINATIONS: Set<String> = setOf("/images", "/gallery", "/files", "/workspace")

fun validateRikkaUi(ui: RikkaUi): List<String> {
    val errors = mutableListOf<String>()
    val keys = mutableSetOf<String>()
    var formId: String? = null
    var nodeCount = 0

    fun walk(node: RikkaUi, depth: Int) {
        nodeCount++
        if (depth > RIKKAUi_MAX_DEPTH) errors += "depth exceeds $RIKKAUi_MAX_DEPTH"
        when (node) {
            is RikkaUi.Column -> {
                if (node.spacing !in 0..32) errors += "column spacing ${node.spacing} out of 0..32"
                node.children.forEach { walk(it, depth + 1) }
            }
            is RikkaUi.Form -> {
                if (formId != null) errors += "only one Form per tree is allowed"
                formId = node.id
                if (node.spacing !in 0..32) errors += "form spacing ${node.spacing} out of 0..32"
                node.children.forEach { walk(it, depth + 1) }
            }
            is RikkaUi.Row -> {
                if (node.spacing !in 0..32) errors += "row spacing ${node.spacing} out of 0..32"
                node.children.forEach { walk(it, depth + 1) }
            }
            is RikkaUi.Input -> if (!keys.add(node.key)) errors += "duplicate key ${node.key}"
            is RikkaUi.Toggle -> if (!keys.add(node.key)) errors += "duplicate key ${node.key}"
            is RikkaUi.Select -> {
                if (!keys.add(node.key)) errors += "duplicate key ${node.key}"
                if (node.options.isEmpty()) errors += "select ${node.key} needs at least one option"
                if (node.options.size != node.options.toSet().size) errors += "select ${node.key} options must be distinct"
                if (node.initial != null && node.initial !in node.options) errors += "select ${node.key} initial not in options"
            }
            is RikkaUi.Progress -> {
                val f = node.fraction
                if (f != null && (f < 0f || f > 1f)) errors += "progress fraction $f out of 0f..1f"
            }
            is RikkaUi.Image -> if (!isSafeUrl(node.url)) errors += "unsafe url scheme in image"
            is RikkaUi.Link -> if (!isSafeUrl(node.url)) errors += "unsafe url scheme in link"
            is RikkaUi.Text -> {}
            is RikkaUi.Button -> validateAction(node.action, errors)
            is RikkaUi.Chip -> node.action?.let { validateAction(it, errors) }
            is RikkaUi.List -> {}
            is RikkaUi.Divider -> {}
        }
    }

    walk(ui, 0)
    if (nodeCount > RIKKAUi_MAX_NODES) errors += "node count $nodeCount exceeds $RIKKAUi_MAX_NODES"
    return errors
}

private fun isSafeUrl(url: String): Boolean = try {
    val scheme = URI(url).scheme
    scheme in setOf("http", "https", "file", "content")
} catch (_: Exception) {
    false
}

private fun validateAction(action: RikkaUiAction, errors: MutableList<String>) {
    if (action is RikkaUiAction.Navigate && action.destination !in DEFAULT_NAV_DESTINATIONS) {
        errors += "navigate destination ${action.destination} not allowlisted"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ai:testDebugUnitTest --tests "*RikkaUiValidatorTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai/src/main/java/me/rerere/ai/ui/RikkaUiValidator.kt ai/src/test/java/me/rerere/ai/ui/RikkaUiValidatorTest.kt
git commit -m "feat: add RikkaUi validation rules"
```

---

### Task 3: Add the render_ui tool

**Files:**
- Create: `ai/src/main/java/me/rerere/ai/tools/RenderUiTool.kt`
- Test: `ai/src/test/java/me/rerere/ai/tools/RenderUiToolTest.kt`

**Interfaces:**
- Consumes: `Tool` (final class: `Tool(name, description, parameters: ()->InputSchema?={null}, systemPrompt: (model,messages)->String={_,_->""}, needsApproval:(JsonElement)->Boolean={false}, execute: suspend (JsonElement)->List<UIMessagePart>)`), `RikkaUi`, `validateRikkaUi`, `UIMessagePart.Text`.
- Produces: `val renderUiTool: Tool` (name `"render_ui"`). Input JSON is the RikkaUi tree serialized via `RikkaUi.serializer()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class RenderUiToolTest {

    @Test
    fun `valid ui returns ok receipt`() = runBlocking {
        val ui: RikkaUi = RikkaUi.Form("s", listOf(RikkaUi.Text("hi")))
        val input = Json.encodeToString(RikkaUi.serializer(), ui)
        val parts = renderUiTool.execute(Json.parseToJsonElement(input))
        assertEquals(1, parts.size)
        assertTrue((parts.single() as UIMessagePart.Text).content.contains("\"ok\":true"))
    }

    @Test
    fun `invalid ui returns error envelope`() = runBlocking {
        val ui: RikkaUi = RikkaUi.Progress(2.0f)
        val input = Json.encodeToString(RikkaUi.serializer(), ui)
        val parts = renderUiTool.execute(Json.parseToJsonElement(input))
        assertTrue((parts.single() as UIMessagePart.Text).content.contains("error"))
    }

    @Test
    fun `generated ui never appears in tool output`() = runBlocking {
        val ui: RikkaUi = RikkaUi.Text("hi")
        val parts = renderUiTool.execute(Json.parseToJsonElement(Json.encodeToString(RikkaUi.serializer(), ui)))
        assertTrue(parts.none { it is UIMessagePart.GeneratedUi })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ai:testDebugUnitTest --tests "*RenderUiToolTest*"`
Expected: FAIL — `renderUiTool` unresolved.

- [ ] **Step 3: Implement the tool**

```kotlin
package me.rerere.ai.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.validateRikkaUi

val renderUiTool: Tool = Tool(
    name = "render_ui",
    description = "Render an interactive RikkaUi tree for the user. Receipt only; the UI is projected for the user automatically.",
    parameters = {
        InputSchema.Obj(
            properties = JsonObject(emptyMap()),
        )
    },
) { json ->
    val tree = runCatching { Json.decodeFromString(RikkaUi.serializer(), json.toString()) }.getOrNull()
        ?: return@Tool listOf(UIMessagePart.Text("""{"ok":false,"error":"invalid_ui_tree"}"""))
    val errors = validateRikkaUi(tree)
    if (errors.isNotEmpty()) {
        listOf(UIMessagePart.Text("""{"ok":false,"error":"invalid_ui_tree","details":${Json.encodeToString(errors)}}"""))
    } else {
        listOf(UIMessagePart.Text("""{"ok":true,"rendered":true}"""))
    }
}
```

Tool is `@Serializable data class Tool(name, description, parameters: ()->InputSchema?={null}, systemPrompt: (model: Model, messages: List<UIMessage>)->String={_,_->""}, needsApproval: (JsonElement)->Boolean={false}, execute: suspend (JsonElement)->List<UIMessagePart>)` (ai/core/Tool.kt). InputSchema has only `Obj(properties: JsonObject, required: List<String>?=null)`. The trailing lambda is the `execute` constructor parameter. `validateRikkaUi` and `RikkaUi.serializer()` come from the ai module (same module, no import needed beyond `me.rerere.ai.ui.*`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ai:testDebugUnitTest --tests "*RenderUiToolTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai/src/main/java/me/rerere/ai/tools/RenderUiTool.kt ai/src/test/java/me/rerere/ai/tools/RenderUiToolTest.kt
git commit -m "feat: add render_ui tool"
```

---

### Task 4: Add renderId to GeneratedUi

**Files:**
- Modify: `ai/src/main/java/me/rerere/ai/ui/Message.kt` (GeneratedUi at ~line 401-406)
- Test: `ai/src/test/java/me/rerere/ai/ui/MessageSerializationTest.kt` (or existing message serialization test file)

**Interfaces:**
- Consumes: Task 3's receipt text convention `{"ok":true,"rendered":true}`.
- Produces: `UIMessagePart.GeneratedUi(renderId: String, ui: RikkaUi, metadata: JsonObject? = null)`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `generated ui round trips with renderId`() {
    val part = UIMessagePart.GeneratedUi(renderId = "call_123", ui = RikkaUi.Text("hi"))
    val json = Json.encodeToString(UIMessagePart.serializer(), part)
    val back = Json.decodeFromString(UIMessagePart.serializer(), json) as UIMessagePart.GeneratedUi
    assertEquals("call_123", back.renderId)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ai:testDebugUnitTest --tests "*GeneratedUi*"`
Expected: FAIL — `renderId` missing (or constructor mismatch).

- [ ] **Step 3: Implement**

Change:

```kotlin
@Serializable
@SerialName("generated_ui")
data class GeneratedUi(
    val ui: RikkaUi,
    override var metadata: JsonObject? = null
) : UIMessagePart()
```

to:

```kotlin
@Serializable
@SerialName("generated_ui")
data class GeneratedUi(
    val renderId: String,
    val ui: RikkaUi,
    override var metadata: JsonObject? = null
) : UIMessagePart()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ai:testDebugUnitTest --tests "*GeneratedUi*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai/src/main/java/me/rerere/ai/ui/Message.kt ai/src/test/java/me/rerere/ai/ui/MessageSerializationTest.kt
git commit -m "feat: add renderId to GeneratedUi"
```

---

### Task 5: Renderer state + new composables

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/message/RikkaUiRenderer.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/components/message/RikkaUiEvent.kt`

**Interfaces:**
- Consumes: `RikkaUi` schema from Task 1, `GeneratedUi.renderId` from Task 4.
- Produces:
  - `sealed interface RikkaUiEvent { data class FormSubmit(val renderId: String, val formId: String, val values: Map<String,String>) : RikkaUiEvent }`
  - `fun RikkaUiRenderer(ui: RikkaUi, renderId: String, onSubmit: (RikkaUiEvent.FormSubmit) -> Unit, onNavigate: (String) -> Unit, modifier: Modifier = Modifier)`
  - `internal val formValuesSaver: Saver<MutableState<Map<String,String>>, *>` (or function `seedFrom(ui): Map<String,String>`).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/me/rerere/rikkahub/ui/components/message/RikkaUiStateTest.kt`:

```kotlin
@Test
fun `seedFrom initializes toggles and inputs`() {
    val ui = RikkaUi.Form("f", listOf(
        RikkaUi.Toggle("enabled", "Enabled", initial = true),
        RikkaUi.Input("name", initial = "Mat"),
        RikkaUi.Input("empty"),
        RikkaUi.Select("mode", "Mode", listOf("a","b")),
    ))
    val seed = seedFrom(ui)
    assertEquals("true", seed["enabled"])
    assertEquals("Mat", seed["name"])
    assertEquals("", seed["empty"])
    assertEquals("", seed["mode"])
}
```

And a FormSubmit conversion test:

```kotlin
@Test
fun `formSubmit converts to canonical text with sorted keys`() {
    val event = RikkaUiEvent.FormSubmit("call_1", "f", mapOf("b" to "2", "a" to "1"))
    val text = formSubmitToText(event)
    assertEquals("""{"type":"rikka_ui_form_submit","renderId":"call_1","formId":"f","values":{"a":"1","b":"2"}}""", text)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*RikkaUiStateTest*"`
Expected: FAIL — `seedFrom`, `formSubmitToText` unresolved.

- [ ] **Step 3: Implement**

In `RikkaUiEvent.kt`:

```kotlin
package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.json.Json

sealed interface RikkaUiEvent {
    data class FormSubmit(
        val renderId: String,
        val formId: String,
        val values: Map<String, String>,
    ) : RikkaUiEvent
}

fun formSubmitToText(event: RikkaUiEvent.FormSubmit): String {
    val sorted = event.values.toSortedMap()
    val json = buildString {
        append("""{"type":"rikka_ui_form_submit","renderId":""").append(Json.encodeToString(event.renderId))
        append(""","formId":""").append(Json.encodeToString(event.formId))
        append(""","values":""").append(Json.encodeToString(sorted))
        append("}")
    }
    return json
}
```

In `RikkaUiRenderer.kt`:

```kotlin
fun seedFrom(ui: RikkaUi): Map<String, String> {
    val out = mutableMapOf<String, String>()
    fun walk(node: RikkaUi) {
        when (node) {
            is RikkaUi.Form -> node.children.forEach(::walk)
            is RikkaUi.Column -> node.children.forEach(::walk)
            is RikkaUi.Row -> node.children.forEach(::walk)
            is RikkaUi.Input -> out[node.key] = node.initial ?: ""
            is RikkaUi.Toggle -> out[node.key] = node.initial.toString()
            is RikkaUi.Select -> out[node.key] = node.initial ?: ""
            else -> {}
        }
    }
    walk(ui)
    return out
}

val formValuesSaver = listSaver<MutableState<Map<String,String>>, Map<String,String>>(
    save = { state -> listOf(state.value) },
    restore = { list -> mutableStateOf(list.first()) },
)
```

Rework the renderer signature and root state:

```kotlin
@Composable
fun RikkaUiRenderer(
    ui: RikkaUi,
    renderId: String,
    onSubmit: (RikkaUiEvent.FormSubmit) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var values by rememberSaveable(renderId, saver = formValuesSaver) { mutableStateOf(seedFrom(ui)) }
    // Body per node branch: render the 14 RikkaUi node types, reading and writing the root state map.
}
```

Update existing `handleAction` so `Navigate` calls `onNavigate(destination)` (through the app-level allowlist) and `Submit` snapshots `values`:

```kotlin
private fun handleAction(
    action: RikkaUiAction,
    renderId: String,
    values: Map<String, String>,
    onSubmit: (RikkaUiEvent.FormSubmit) -> Unit,
    onNavigate: (String) -> Unit,
    clipboard: ClipboardManager,
    context: Context,
) {
    when (action) {
        is RikkaUiAction.Copy -> clipboard.setText(AnnotatedString(action.text))
        is RikkaUiAction.OpenUrl -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))
        is RikkaUiAction.Submit -> onSubmit(RikkaUiEvent.FormSubmit(renderId, action.formId, values))
        is RikkaUiAction.Navigate -> onNavigate(action.destination)
    }
}
```

Add renderer branches: `Form` → sub-`Column` scoped by formId (renderer root state already keyed by renderId); `Row` → Compose `Row(verticalArrangement = Arrangement.spacedBy(spacing.dp), verticalAlignment = when(v){"top"->Alignment.Top;"bottom"->Alignment.Bottom;else->Alignment.CenterVertically})`; `Input` → single-line `OutlinedTextField(value=values[key]?:"" , onValueChange={ values=values+(key to it) }, placeholder, label)`; `Toggle` → `Row{Switch(checked=values[key]=="true", onCheckedChange={values=values+(key to it.toString())}); Text(label)}`; `Select` → `ExposedDropdownMenuBox` with options, selection writes `values=values+(key to option)`; `Progress` → `LinearProgressIndicator(progress = if(fraction==null) null else {fraction}, ...)` (indeterminate when null); `Link` → `TextButton(onClick={}){Text(label)}` with URL open via handleAction(OpenUrl). Wrap each branch so `values` update happens per keystroke locally with NO chat event.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*RikkaUiStateTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/components/message/RikkaUiRenderer.kt app/src/main/java/me/rerere/rikkahub/ui/components/message/RikkaUiEvent.kt app/src/test/java/me/rerere/rikkahub/ui/components/message/RikkaUiStateTest.kt
git commit -m "feat: render interactive RikkaUi state"
```

---

### Task 6: Lift successful render_ui output into the message

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/ai/GenerationHandlerLiftTest.kt` (new, JVM)

**Interfaces:**
- Consumes: `renderUiTool` receipt `{"ok":true,"rendered":true}`; `UIMessagePart.GeneratedUi(renderId, ui)`; `UIMessagePart.Tool.toolCallId`/`inputAsJson`.
- Produces: `fun liftRenderedUi(parts: List<UIMessagePart>): List<UIMessagePart>` — pure function, appended AFTER the Tool block, idempotent by renderId.

- [ ] **Step 1: Write the failing tests**

```kotlin
class GenerationHandlerLiftTest {

    private val successTool = { id: String, ui: RikkaUi ->
        UIMessagePart.Tool(
            toolCallId = id, toolName = "render_ui",
            input = Json.encodeToString(JsonObject(mapOf("type" to JsonPrimitive("ui_column"), "children" to JsonArray(emptyList())))),
            output = listOf(UIMessagePart.Text("""{"ok":true,"rendered":true}""")),
        )
    }

    @Test
    fun `successful render_ui produces generated ui after tool block`() {
        val tools = listOf(successTool("t1", RikkaUi.Text("x")))
        val lifted = liftRenderedUi(tools)
        assertEquals(2, lifted.size)
        assertTrue(lifted.last() is UIMessagePart.GeneratedUi)
        assertEquals("t1", (lifted.last() as UIMessagePart.GeneratedUi).renderId)
    }

    @Test
    fun `failed receipt never creates generated ui`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "t1", toolName = "render_ui",
            input = "{}",
            output = listOf(UIMessagePart.Text("""{"ok":false,"error":"invalid_ui_tree"}""")),
        )
        assertEquals(1, liftRenderedUi(listOf(tool)).size)
    }

    @Test
    fun `consecutive tools stay contiguous`() {
        val tools = listOf(successTool("a", RikkaUi.Text("x")), successTool("b", RikkaUi.Text("y")))
        val lifted = liftRenderedUi(tools)
        assertEquals(4, lifted.size)
        assertEquals("a", (lifted[0] as UIMessagePart.Tool).toolCallId)
        assertEquals("b", (lifted[1] as UIMessagePart.Tool).toolCallId)
        assertTrue(lifted[2] is UIMessagePart.GeneratedUi)
        assertTrue(lifted[3] is UIMessagePart.GeneratedUi)
    }

    @Test
    fun `distinct render ids and idempotent re-run`() {
        val tools = listOf(successTool("a", RikkaUi.Text("x")))
        val once = liftRenderedUi(tools)
        val twice = liftRenderedUi(once)
        assertEquals(2, once.size)
        assertEquals(2, twice.size) // no duplicate renderId added
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*GenerationHandlerLiftTest*"`
Expected: FAIL — `liftRenderedUi` unresolved.

- [ ] **Step 3: Implement**

Add to `GenerationHandler.kt` (or a small sibling file `app/.../data/ai/RenderedUiLift.kt`):

```kotlin
fun liftRenderedUi(parts: List<UIMessagePart>): List<UIMessagePart> {
    val lifted = mutableListOf<UIMessagePart>()
    val existingRenderIds = parts.filterIsInstance<UIMessagePart.GeneratedUi>().map { it.renderId }.toSet()
    parts.forEach { part ->
        lifted += part
        if (part is UIMessagePart.Tool && part.toolName == "render_ui" && part.isExecuted) {
            val receipt = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.content }
            if (receipt.contains("\"ok\":true") && receipt.contains("\"rendered\":true")) {
                if (part.toolCallId !in existingRenderIds) {
                    val ui = runCatching {
                        Json.decodeFromString(RikkaUi.serializer(), part.inputAsJson.toString())
                    }.getOrNull()
                    if (ui != null && validateRikkaUi(ui).isEmpty()) {
                        lifted += UIMessagePart.GeneratedUi(renderId = part.toolCallId, ui = ui)
                    }
                }
            }
        }
    }
    return lifted
}
```

Wire into the existing post-execution seam in `GenerationHandler.kt` where `messages` is rebuilt with updated Tool parts: after computing `updatedParts` and `messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)`, apply `liftRenderedUi` to the final `parts` of the rebuilt assistant message (re-parse the tool's `inputAsJson` to derive the ui; the input was validated at tool-execution time so it will pass again). Keep tool-call/result grouping intact (all Tools first, GeneratedUi appended after).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*GenerationHandlerLiftTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt app/src/main/java/me/rerere/rikkahub/data/ai/RenderedUiLift.kt app/src/test/java/me/rerere/rikkahub/data/ai/GenerationHandlerLiftTest.kt
git commit -m "feat: lift rendered ui into assistant message"
```

---

### Task 7: Chat ingress — FormSubmit to user text turn

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`, `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt`, `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/chat/ChatVMFormSubmitTest.kt` (JVM where possible; else instrumented on disposable install)

**Interfaces:**
- Consumes: `formSubmitToText` from Task 5; `RikkaUiEvent.FormSubmit`.
- Produces: chat ingress that converts a FormSubmit to `UIMessagePart.Text(formSubmitToText(event))` and sends via existing `ChatVM.handleMessageSend(List<UIMessagePart>)`.

- [ ] **Step 1: Write the failing test (JVM)**

`ChatVMFormSubmitTest.kt` (pure conversion, no Android dependencies):

```kotlin
@Test
fun `form submit becomes a user text turn`() {
    val event = RikkaUiEvent.FormSubmit("call_9", "settings", mapOf("tone" to "warm", "notifications" to "true"))
    val parts = formSubmitToUserTurn(event)
    assertEquals(1, parts.size)
    val text = (parts.single() as UIMessagePart.Text).content
    assertTrue(text.contains("\"type\":\"rikka_ui_form_submit\""))
    assertTrue(text.contains("\"renderId\":\"call_9\""))
    assertTrue(text.contains("\"formId\":\"settings\""))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChatVMFormSubmitTest*"`
Expected: FAIL — `formSubmitToUserTurn` unresolved.

- [ ] **Step 3: Implement**

Add helper near the event type (Task 5 file):

```kotlin
fun formSubmitToUserTurn(event: RikkaUiEvent.FormSubmit): List<UIMessagePart> =
    listOf(UIMessagePart.Text(formSubmitToText(event)))
```

In `ChatVM.kt`, add:

```kotlin
fun submitForm(event: RikkaUiEvent.FormSubmit) {
    handleMessageSend(formSubmitToUserTurn(event))
}
```

In `ChatPage.kt` / `ChatMessage.kt`: thread `renderId`, `onSubmit = { vm.submitForm(it) }`, `onNavigate` through a nav allowlist (`if (destination in navAllowlist) navigator.navigate(destination)`; allowlist mirrors `DEFAULT_NAV_DESTINATIONS`). The `ChatMessage.kt:635` seam becomes:

```kotlin
is UIMessagePart.GeneratedUi -> {
    RikkaUiRenderer(ui = part.ui, renderId = part.renderId, onSubmit = onSubmit, onNavigate = onNavigate)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChatVMFormSubmitTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt app/src/test/java/me/rerere/rikkahub/ui/pages/chat/ChatVMFormSubmitTest.kt
git commit -m "feat: route form submissions into chat"
```

---

## Verification Gate

```bash
./gradlew :ai:testDebugUnitTest :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
```

Expected: all green. No device test this phase. Do NOT run `connectedDebugAndroidTest` on the primary phone (AGP uninstalls the target package after the run).
