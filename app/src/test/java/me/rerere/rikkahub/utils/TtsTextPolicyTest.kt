package me.rerere.rikkahub.utils

import me.rerere.rikkahub.data.datastore.DisplaySetting
import org.junit.Assert.*
import org.junit.Test

class TtsTextPolicyTest {
    @Test fun allOptionsOffLeavesTextIntact() {
        assertEquals("Unquoted text (aside)", prepareTtsText(
            "  Unquoted text (aside)  ", DisplaySetting()
        ))
    }

    @Test fun quotedOnlyNeverFallsBackToFullMessage() {
        val options = DisplaySetting(ttsOnlyReadQuoted = true)
        assertNull(prepareTtsText("No quoted content here", options))
        assertNull(prepareTtsText("""Someone said "" with no actual words.""", options))
        assertEquals("Hello!", prepareTtsText("""Aside: "Hello!" End.""", options))
    }

    @Test fun bracketOnlyNeverReadsExcludedText() {
        val options = DisplaySetting(ttsOnlyReadOutsideBrackets = true)
        assertNull(prepareTtsText("(whispered)", options))
        assertNull(prepareTtsText("[stage direction]", options))
        assertEquals("Hello world", prepareTtsText(
            "Hello [aside] world", options
        )?.replace(Regex("\\s+"), " "))
    }

    @Test fun combinedOptionsFilterInSpeechOrderWithoutLeakage() {
        val options = DisplaySetting(
            ttsOnlyReadQuoted = true, ttsOnlyReadOutsideBrackets = true,
        )
        assertEquals("Hello\nWorld", prepareTtsText(
            """Narration: "Hello[whisper]" and 「World」 (end)""", options
        ))
        assertNull(prepareTtsText("""(laughing) "([only directions])" """, options))
        assertNull(prepareTtsText("There were no quotes here", options))
    }

    @Test fun combinedOptionsNeverChangeInputsOrStoredConfiguration() {
        val options = DisplaySetting(ttsOnlyReadQuoted = true, ttsOnlyReadOutsideBrackets = true)
        val original = """Speaker: "line [aside] here" """
        assertEquals("line  here", prepareTtsText(original, options))
        assertEquals("""Speaker: "line [aside] here" """, original)
        assertTrue(options.ttsOnlyReadQuoted)
    }
}
