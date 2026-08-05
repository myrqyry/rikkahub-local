package me.rerere.rikkahub.data.modelregistry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.uuid.Uuid
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Test

class SpeechSelectionFacadeTest {
    @Test
    fun assistantTtsOverrideWinsWithoutChangingGlobalSelection() {
        val global = TTSProviderSetting.SystemTTS(id = Uuid.random(), name = "Global")
        val override = TTSProviderSetting.SystemTTS(id = Uuid.random(), name = "Assistant")
        val settings = Settings(
            ttsProviders = listOf(global, override),
            selectedTTSProviderId = global.id,
        )
        val assistant = Assistant(ttsProviderOverrideId = override.id)

        val result = SpeechSelectionFacade.resolveTtsProvider(assistant, settings)

        assertEquals(override.id, result.provider?.id)
        assertEquals(global.id, settings.selectedTTSProviderId)
    }

    @Test
    fun deletedAsrOverrideFallsBackToGlobalProvider() {
        val global = ASRProviderSetting.OpenAIRealtime(id = Uuid.random(), name = "Global")
        val settings = Settings(
            asrProviders = listOf(global),
            selectedASRProviderId = global.id,
        )
        val assistant = Assistant(asrProviderOverrideId = Uuid.random())

        val result = SpeechSelectionFacade.resolveAsrProvider(assistant, settings)

        assertEquals(global.id, result.provider?.id)
    }

    @Test
    fun missingSpeechProviderIsExplicit() {
        val result = SpeechSelectionFacade.resolveTtsProvider(Assistant(), Settings(ttsProviders = emptyList()))

        assertTrue(result is SpeechProviderResolution.Unavailable)
        assertEquals(null, result.provider)
    }
}
