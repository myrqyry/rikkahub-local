package me.rerere.rikkahub.ui.components.message

import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RikkaUiStateTest {

    @Test
    fun `seedFrom initializes toggles and inputs`() {
        val ui = RikkaUi.Form("f", listOf(
            RikkaUi.Toggle("enabled", "Enabled", initial = true),
            RikkaUi.Input("name", initial = "Mat"),
            RikkaUi.Input("empty"),
            RikkaUi.Select("mode", "Mode", listOf("a", "b")),
        ))
        val seed = seedFrom(ui)
        assertEquals("true", seed["enabled"])
        assertEquals("Mat", seed["name"])
        assertEquals("", seed["empty"])
        assertEquals("", seed["mode"])
    }

    @Test
    fun `formSubmit converts to canonical text with sorted keys`() {
        val event = RikkaUiEvent.FormSubmit("call_1", "f", mapOf("b" to "2", "a" to "1"))
        val text = formSubmitToText(event)
        assertEquals(
            """{"type":"rikka_ui_form_submit","renderId":"call_1","formId":"f","values":{"a":"1","b":"2"}}""",
            text,
        )
    }

    @Test
    fun `formSubmitToUserTurn produces a single text part`() {
        val event = RikkaUiEvent.FormSubmit("call_9", "settings", mapOf("enabled" to "true"))
        val parts = formSubmitToUserTurn(event)
        assertEquals(1, parts.size)
        val text = (parts.single() as UIMessagePart.Text).text
        assertTrue(text.contains("rikka_ui_form_submit"))
        assertTrue(text.contains("call_9"))
        assertTrue(text.contains("settings"))
    }
}
