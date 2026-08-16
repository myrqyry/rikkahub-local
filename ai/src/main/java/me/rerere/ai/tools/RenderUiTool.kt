package me.rerere.ai.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.validateRikkaUi

/**
 * Phase G. Emits an interactive RikkaUi tree for the user. The tool returns a model-facing
 * receipt only; the UI itself is projected for the user by the app-side lift seam.
 */
val renderUiTool: Tool = Tool(
    name = "render_ui",
    description = "Render an interactive RikkaUi tree for the user. Receipt only; the UI is projected for the user automatically.",
    parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
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
