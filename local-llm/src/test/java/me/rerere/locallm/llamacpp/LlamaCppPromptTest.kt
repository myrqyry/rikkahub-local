package me.rerere.locallm.llamacpp

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppPromptTest {

    private fun text(role: MessageRole, vararg parts: String) = UIMessage(
        role = role,
        parts = parts.map { UIMessagePart.Text(it) },
    )

    @Test fun `single user message yields empty context and that message as user`() {
        val req = LlamaCppPrompt.build(listOf(text(MessageRole.USER, "hello")))
        assertEquals("", req.context)
        assertEquals("hello", req.user)
    }

    @Test fun `prior turns render as chatml context`() {
        val req = LlamaCppPrompt.build(
            listOf(
                text(MessageRole.USER, "hi"),
                text(MessageRole.ASSISTANT, "hey"),
                text(MessageRole.USER, "bye"),
            ),
        )
        assertEquals("User: hi\nAssistant: hey", req.context)
        assertEquals("bye", req.user)
    }

    @Test fun `system message becomes system and is excluded from context`() {
        val req = LlamaCppPrompt.build(
            listOf(
                text(MessageRole.SYSTEM, "be brief"),
                text(MessageRole.USER, "q"),
            ),
        )
        assertEquals("be brief", req.system)
        assertEquals("", req.context)
        assertEquals("q", req.user)
    }

    @Test fun `no user message falls back to full context and empty user`() {
        val req = LlamaCppPrompt.build(listOf(text(MessageRole.ASSISTANT, "hi")))
        assertEquals("Assistant: hi", req.context)
        assertEquals("", req.user)
    }

    @Test fun `rawTexts flattens each message to its text parts`() {
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text("a "), UIMessagePart.Text("b")),
            ),
            text(MessageRole.ASSISTANT, "c"),
        )
        assertEquals(listOf("a b", "c"), LlamaCppPrompt.rawTexts(messages))
    }

    @Test fun `context is capped at the history char budget`() {
        val big = "a".repeat(1000)
        val req = LlamaCppPrompt.build(
            listOf(
                text(MessageRole.USER, big),
                text(MessageRole.ASSISTANT, big),
                text(MessageRole.USER, "q"),
            ),
        )
        assertTrue(req.context.length <= LlamaCppPrompt.HISTORY_CHAR_BUDGET)
        assertEquals("q", req.user)
    }
}
