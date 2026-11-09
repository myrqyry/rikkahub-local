package me.rerere.rikkahub.data.datastore

import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class TtsDefaultSeedingTest {
    private val system = TTSProviderSetting.SystemTTS(id = DEFAULT_SYSTEM_TTS_ID, name = "System")
    private val aiHub = TTSProviderSetting.OpenAI(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "AiHubMix",
    )
    private val bundled = listOf(system, aiHub)

    @Test fun newInstallationStillReceivesStarterProviders() {
        assertEquals(bundled, restoreTtsDefaults(null, bundled, system))
    }

    @Test fun removingAiHubMixSticksAcrossSettingsReadsAndAppRestarts() {
        val saved = listOf(system)
        val hydrated = restoreTtsDefaults(saved, bundled, system)
        assertEquals(saved, hydrated)
        assertEquals(saved, restoreTtsDefaults(hydrated, bundled, system))
        assertFalse(hydrated.any { it.id == aiHub.id })
    }

    @Test fun explicitlySavedEmptyListOnlyRecoversRequiredSystemVoice() {
        assertEquals(listOf(system), restoreTtsDefaults(emptyList(), bundled, system))
    }

    @Test fun restoresMissingSystemVoiceWithoutReintroducingAiHubMix() {
        val custom = TTSProviderSetting.OpenAI(
            id = Uuid.parse("00000000-0000-0000-0000-000000000552"),
            name = "Custom",
        )
        val updated = restoreTtsDefaults(listOf(custom), bundled, system)
        assertEquals(listOf(custom, system), updated)
        assertTrue(updated.none { it.id == aiHub.id })
    }
}
