package me.rerere.rikkahub.utils

import me.rerere.rikkahub.data.datastore.TtsPlaybackPreferences
import org.junit.Assert.*
import org.junit.Test

class TtsTextPolicyTest {
    @Test fun allOptionsOffLeavesTextIntact() {
        assertEquals("Unquoted text (aside)", prepareTtsText(
            "  Unquoted text (aside)  ", TtsPlaybackPreferences()
        ))
    }

    @Test fun quotedOnlyNeverFallsBackToFullMessage() {
        val options = TtsPlaybackPreferences(quotedOnly = true)
        assertNull(prepareTtsText("No quoted content here", options))
        assertNull(prepareTtsText("""Someone said "" with no actual words.""", options))
        assertEquals("Hello!", prepareTtsText("""Aside: "Hello!" End.""", options))
    }

    @Test fun bracketOnlyNeverReadsExcludedText() {
        val options = TtsPlaybackPreferences(skipBracketed = true)
        assertNull(prepareTtsText("(whispered)", options))
        assertNull(prepareTtsText("[stage direction]", options))
        assertEquals("Hello world", prepareTtsText(
            "Hello [aside] world", options
        )?.replace(Regex("\\s+"), " "))
    }

    @Test fun combinedOptionsFilterInSpeechOrderWithoutLeakage() {
        val options = TtsPlaybackPreferences(
            quotedOnly = true, skipBracketed = true,
        )
        assertEquals("Hello\nWorld", prepareTtsText(
            """Narration: "Hello[whisper]" and 「World」 (end)""", options
        ))
        assertNull(prepareTtsText("""(laughing) "([only directions])" """, options))
        assertNull(prepareTtsText("There were no quotes here", options))
    }

    @Test fun combinedOptionsNeverChangeInputsOrStoredConfiguration() {
        val options = TtsPlaybackPreferences(quotedOnly = true, skipBracketed = true)
        val original = """Speaker: "line [aside] here" """
        assertEquals("line  here", prepareTtsText(original, options))
        assertEquals("""Speaker: "line [aside] here" """, original)
        assertTrue(options.quotedOnly)
    }
}
