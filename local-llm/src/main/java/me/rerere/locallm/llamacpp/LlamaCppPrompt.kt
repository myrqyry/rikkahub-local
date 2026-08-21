package me.rerere.locallm.llamacpp

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

data class LlamaCppPromptRequest(
    val system: String,
    val context: String,
    val user: String,
)

/**
 * Renders a chat message list into the (system, context, user) triple that
 * Llamatik's [LlamaBridge.generateWithContextStream] expects.
 *
 * The last USER message becomes `user`; everything before it renders as ChatML
 * in `context`; SYSTEM text becomes `system`. Kept pure and dependency-free so
 * the shaping logic is JVM-unit-testable (the native bridge is not).
 */
object LlamaCppPrompt {

    const val SYSTEM_CHAR_BUDGET = 500
    const val HISTORY_CHAR_BUDGET = 3000

    fun build(messages: List<UIMessage>): LlamaCppPromptRequest {
        val system = messages.asSequence()
            .filter { it.role == MessageRole.SYSTEM }
            .flatMap { it.parts.asSequence() }
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
            .take(SYSTEM_CHAR_BUDGET)

        val turns = messages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        val lastUserIndex = turns.indexOfLast { it.role == MessageRole.USER }
        val (contextTurns, user) = if (lastUserIndex >= 0) {
            turns.subList(0, lastUserIndex) to rawText(turns[lastUserIndex])
        } else {
            turns to ""
        }

        var context = renderHistoryAsChatML(contextTurns)
        var remaining = contextTurns
        while (context.length > HISTORY_CHAR_BUDGET && remaining.size > 1) {
            remaining = remaining.drop(1)
            context = renderHistoryAsChatML(remaining)
        }
        if (context.length > HISTORY_CHAR_BUDGET) {
            context = context.take(HISTORY_CHAR_BUDGET)
        }

        return LlamaCppPromptRequest(system = system, context = context, user = user)
    }

    private fun rawText(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun renderHistoryAsChatML(messages: List<UIMessage>): String = buildString {
        for (msg in messages) {
            val text = rawText(msg)
            if (text.isEmpty()) continue
            if (isNotEmpty()) append("\n")
            append(if (msg.role == MessageRole.USER) "User: " else "Assistant: ")
            append(text)
        }
    }
}
