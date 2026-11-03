package me.rerere.rikkahub.data.ai.prompts

import java.time.Instant
import kotlin.uuid.Uuid
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation

/**
 * Compact readable evidence for summary generation. Non-text content is
 * *referenced*, not silently dropped or hallucinated. Actual source files
 * remain with the pre-compression archive and are not sent as model inputs.
 */
internal fun UIMessage.toCompressionSource(maxTextChars: Int = 2000): String {
    val pieces = parts.map { part ->
        when (part) {
            is UIMessagePart.Text -> part.text.take(maxTextChars) +
                if (part.text.length > maxTextChars) " [text truncated]" else ""
            is UIMessagePart.Image -> "[Image attached: ${part.url.safeCompressionRef()} — visual content not included]"
            is UIMessagePart.Video -> "[Video attached: ${part.url.safeCompressionRef()} — visual content not included]"
            is UIMessagePart.Audio -> "[Audio attached: ${part.url.safeCompressionRef()} — audio content not included]"
            is UIMessagePart.Document -> "[Document: ${part.fileName.take(120)}, ${part.mime.take(80)}, ${part.url.safeCompressionRef()} — file content not included]"
            is UIMessagePart.Tool -> "[Tool ${part.toolName.take(110)} (id ${part.toolCallId.take(80)}): " +
                if (part.isExecuted) {
                    "output present; excerpt: ${part.output.filterIsInstance<UIMessagePart.Text>().joinToString(" ") { it.text }.take(600)}]"
                } else if (part.isInterruptedAttempt) "started but outcome unknown]"
                else "not confirmed executed; state ${part.approvalState}]"
            is UIMessagePart.ToolCall -> "[Tool request ${part.toolName.take(110)}; execution unverified]"
            is UIMessagePart.ToolResult -> "[Tool result ${part.toolName.take(110)}: ${part.content.toString().take(600)}]"
            is UIMessagePart.Reasoning -> "[Reasoning content not included]"
            is UIMessagePart.GeneratedUi -> "[Interactive UI id ${part.renderId.take(80)}; details not included]"
            else -> "[Non-text content omitted; see archived conversation]"
        }
    }
    return "[${role.name}]: " + pieces.joinToString("\n")
}

private fun String.safeCompressionRef(): String =
    if (startsWith("data:", ignoreCase = true)) "[embedded data]"
    else take(220) + if (length > 220) "..." else ""

/** A new, visible conversation containing the original message tree. No
 * replacement occurs until the archival insert has succeeded. Caller owns
 * file URL cloning, including nested tool outputs and all message variants.
 */
internal fun Conversation.preCompressionBackup(
    duplicatePart: (UIMessagePart) -> UIMessagePart,
): Conversation {
    val timestamp = Instant.now()
    return copy(
        id = Uuid.random(),
        title = "Before compression — " + (title.ifBlank { "Untitled chat" }).take(80),
        messageNodes = messageNodes.map { node ->
            node.copy(
                id = Uuid.random(),
                messages = node.messages.map { msg ->
                    msg.copy(parts = msg.parts.map(duplicatePart))
                },
            )
        },
        isPinned = false,
        createAt = timestamp,
        updateAt = timestamp,
        revision = 0L,
        newConversation = false,
    )
}

/** The model can return blank, whitespace-only, or a refusal. These cannot
 * replace history. A minimum is intentionally modest for very short histories.
 */
internal fun checkedCompressedSummary(raw: String?): String {
    val text = raw?.trim().orEmpty()
    require(text.isNotBlank()) { "Compression returned no summary; original conversation was not changed" }
    return text
}
