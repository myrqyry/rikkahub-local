package me.rerere.locallm.litert

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGemmaMobileActionsPromptTest {

    @Test
    fun `prompt includes exact local datetime weekday and function-calling cue`() {
        val now = ZonedDateTime.of(
            2026, 8, 7, 12, 30, 45, 0,
            ZoneId.of("America/Chicago"),
        )

        assertEquals(
            "Current date and time given in YYYY-MM-DDTHH:MM:SS format: 2026-08-07T12:30:45\n" +
                "Day of week is Friday\n" +
                "You are a model that can do function calling with the following functions.",
            FunctionGemmaMobileActionsPrompt.build(now),
        )
    }

    @Test
    fun `prompt uses local wall clock rather than UTC conversion`() {
        val now = ZonedDateTime.of(
            2026, 1, 2, 0, 5, 6, 0,
            ZoneId.of("America/Los_Angeles"),
        )
        val prompt = FunctionGemmaMobileActionsPrompt.build(now)

        assertTrue(prompt.contains("2026-01-02T00:05:06"))
        assertTrue(prompt.contains("Day of week is Friday"))
        assertFalse(prompt.contains("2026-01-02T08:05:06"))
    }

    @Test
    fun `prompt stays compact and does not duplicate tool schemas`() {
        val prompt = FunctionGemmaMobileActionsPrompt.build(
            ZonedDateTime.of(2026, 8, 7, 12, 0, 0, 0, ZoneId.of("UTC")),
        )

        assertTrue(prompt.length < 240)
        assertFalse(prompt.contains("turn_on_flashlight"))
        assertFalse(prompt.contains("properties"))
    }
}
