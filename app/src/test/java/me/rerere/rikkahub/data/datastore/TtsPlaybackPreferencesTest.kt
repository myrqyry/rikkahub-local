package me.rerere.rikkahub.data.datastore

import kotlin.uuid.Uuid
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.*
import org.junit.Test

class TtsPlaybackPreferencesTest {
    @Test fun legacyOptionsAreInheritedWithoutRewritingSavedData() {
        // Mimics a prior installation: no ttsPlaybackProfiles field exists.
        val old = JsonInstant.decodeFromString<DisplaySetting>(
            """{"ttsOnlyReadQuoted":true,"ttsOnlyReadOutsideBrackets":true,
               "autoPlayTTSAfterGeneration":true}"""
        )
        val alpha = Uuid.random()
        assertEquals(TtsPlaybackPreferences(true, true, true), old.playbackFor(alpha))
        assertTrue(old.ttsPlaybackProfiles.isEmpty())
        val unchanged = old.withPlaybackFor(alpha, TtsPlaybackPreferences())
        assertTrue(unchanged.ttsOnlyReadQuoted)
        assertTrue(unchanged.ttsOnlyReadOutsideBrackets)
        assertTrue(unchanged.autoPlayTTSAfterGeneration)
        assertEquals(TtsPlaybackPreferences(), unchanged.playbackFor(alpha))
        assertEquals(TtsPlaybackPreferences(true, true, true), unchanged.playbackFor(Uuid.random()))
    }

    @Test fun oneProviderCanDifferWithoutChangingAnotherOrUnrelatedSettings() {
        val a = Uuid.random()
        val b = Uuid.random()
        val before = DisplaySetting(
            fontSizeRatio = 1.25f,
            showTokenUsage = false,
        )
        val custom = TtsPlaybackPreferences(quotedOnly = true, skipBracketed = true, autoPlay = true)
        val newSetting = before.withPlaybackFor(a, custom)
        assertEquals(custom, newSetting.playbackFor(a))
        assertEquals(TtsPlaybackPreferences(), newSetting.playbackFor(b))
        assertEquals(1.25f, newSetting.fontSizeRatio)
        assertFalse(newSetting.showTokenUsage)
        assertEquals(before, DisplaySetting(
            fontSizeRatio = newSetting.fontSizeRatio,
            showTokenUsage = newSetting.showTokenUsage,
        ))
        val second = newSetting.withPlaybackFor(b, TtsPlaybackPreferences(autoPlay = true))
        assertEquals(custom, second.playbackFor(a))
        assertFalse(second.playbackFor(b).quotedOnly)
        assertTrue(second.playbackFor(b).autoPlay)
    }

    @Test fun persistedProviderProfilesRoundTripThroughDisplaySettingsJson() {
        val id = Uuid.random()
        val profile = TtsPlaybackPreferences(quotedOnly = true, autoPlay = true)
        val setting = DisplaySetting(ttsOnlyReadOutsideBrackets = true)
            .withPlaybackFor(id, profile)
        val roundTrip = JsonInstant.decodeFromString<DisplaySetting>(
            JsonInstant.encodeToString(setting)
        )
        assertEquals(setting, roundTrip)
        assertEquals(profile, roundTrip.playbackFor(id))
    }

    @Test fun selectedProviderControlsActualPlaybackProfile() {
        val a = TTSProviderSetting.SystemTTS(id = Uuid.random(), name = "A")
        val b = TTSProviderSetting.SystemTTS(id = Uuid.random(), name = "B")
        val display = DisplaySetting()
            .withPlaybackFor(a.id, TtsPlaybackPreferences(quotedOnly = true))
            .withPlaybackFor(b.id, TtsPlaybackPreferences(autoPlay = true))
        val aSelected = Settings(
            ttsProviders = listOf(a, b),
            selectedTTSProviderId = a.id,
            displaySetting = display,
        )
        assertTrue(aSelected.selectedTtsPlayback().quotedOnly)
        assertFalse(aSelected.selectedTtsPlayback().autoPlay)
        val bSelected = aSelected.copy(selectedTTSProviderId = b.id)
        assertTrue(bSelected.selectedTtsPlayback().autoPlay)
        assertFalse(bSelected.selectedTtsPlayback().quotedOnly)
    }
}
