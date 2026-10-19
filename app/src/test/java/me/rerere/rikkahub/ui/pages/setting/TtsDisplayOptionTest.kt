package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.datastore.DisplaySetting
import org.junit.Assert.*
import org.junit.Test

class TtsDisplayOptionTest {
    @Test fun togglesPreserveOtherUserSettings() {
        val before = DisplaySetting(
            ttsOnlyReadQuoted = false,
            ttsOnlyReadOutsideBrackets = false,
            autoPlayTTSAfterGeneration = false,
            fontSizeRatio = 1.35f,
            showTokenUsage = false,
        )
        val after = before
            .withTtsOption(TtsDisplayOption.QUOTED_ONLY, true)
            .withTtsOption(TtsDisplayOption.SKIP_BRACKETS, true)
            .withTtsOption(TtsDisplayOption.AUTO_PLAY, true)
        assertTrue(after.ttsOnlyReadQuoted)
        assertTrue(after.ttsOnlyReadOutsideBrackets)
        assertTrue(after.autoPlayTTSAfterGeneration)
        assertEquals(1.35f, after.fontSizeRatio)
        assertFalse(after.showTokenUsage)
        assertEquals(before, after
            .withTtsOption(TtsDisplayOption.QUOTED_ONLY, false)
            .withTtsOption(TtsDisplayOption.SKIP_BRACKETS, false)
            .withTtsOption(TtsDisplayOption.AUTO_PLAY, false))
    }
}
