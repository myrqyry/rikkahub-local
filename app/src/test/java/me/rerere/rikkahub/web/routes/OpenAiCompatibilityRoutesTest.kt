package me.rerere.rikkahub.web.routes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.web.dto.OpenAiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OpenAiCompatibilityRoutesTest {
    @Test
    fun `developer role maps to system without flattening chat history`() {
        val message = openAiMessageToUiMessage(
            OpenAiChatMessage(
                role = "developer",
                content = JsonPrimitive("Keep answers compact."),
            )
        )

        assertEquals(MessageRole.SYSTEM, message.role)
        assertEquals(
            "Keep answers compact.",
            (message.parts.single() as UIMessagePart.Text).text,
        )
    }

    @Test
    fun `text content arrays are accepted and concatenated`() {
        val content = Json.parseToJsonElement(
            """[
                {"type":"text","text":"hello "},
                {"type":"input_text","text":"world"}
            ]"""
        )

        assertEquals("hello world", openAiTextContent(content))
    }

    @Test
    fun `multimodal content is rejected instead of silently dropped`() {
        val content = Json.parseToJsonElement(
            """[
                {"type":"text","text":"describe this"},
                {"type":"image_url","image_url":{"url":"data:image/png;base64,abc"}}
            ]"""
        )

        try {
            openAiTextContent(content)
            fail("Expected unsupported multimodal content to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: v1 is deliberately honest about text-only compatibility.
        }
    }

    @Test
    fun `tool role is rejected until OpenAI tool semantics are implemented`() {
        try {
            openAiMessageToUiMessage(
                OpenAiChatMessage(
                    role = "tool",
                    content = JsonPrimitive("result"),
                )
            )
            fail("Expected tool-role messages to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
