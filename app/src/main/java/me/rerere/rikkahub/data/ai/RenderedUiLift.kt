package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.validateRikkaUi

/**
 * Phase G (RikkaUI). Deterministically lifts successful `render_ui` tool calls into
 * `GeneratedUi` parts so the human projection renders as a sibling part of the assistant
 * message while the Tool keeps its model-facing receipt.
 *
 * Rules:
 * - Only tools named `render_ui` with an executed receipt `{"ok":true,"rendered":true}` lift.
 * - All Tool parts stay contiguous (RikkaHub groups consecutive executed tools so provider
 *   adapters preserve tool-call/result boundaries); lifted parts are appended AFTER the block.
 * - Idempotent: a renderId already present on a `GeneratedUi` is never re-added.
 * - The ui tree is re-decoded from the tool's args and re-validated before lifting.
 */
fun liftRenderedUi(parts: List<UIMessagePart>): List<UIMessagePart> {
    val existingRenderIds = parts
        .filterIsInstance<UIMessagePart.GeneratedUi>()
        .map { it.renderId }
        .toSet()
    val lifted = mutableListOf<UIMessagePart>()
    val appended = mutableListOf<UIMessagePart.GeneratedUi>()
    parts.forEach { part ->
        lifted += part
        if (part is UIMessagePart.Tool &&
            part.toolName == "render_ui" &&
            part.isExecuted &&
            part.toolCallId !in existingRenderIds
        ) {
            val receipt = part.output
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }
            if (receipt.contains("\"ok\":true") && receipt.contains("\"rendered\":true")) {
                val ui = runCatching {
                    Json.decodeFromString(RikkaUi.serializer(), part.inputAsJson().toString())
                }.getOrNull()
                if (ui != null && validateRikkaUi(ui).isEmpty()) {
                    appended += UIMessagePart.GeneratedUi(renderId = part.toolCallId, ui = ui)
                }
            }
        }
    }
    return lifted + appended
}
