package me.rerere.tts.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MediaPipeTtsSettingTest {
    @Test fun providerTypesAreDistinctAndSerializable() {
        val phonon: TTSProviderSetting = TTSProviderSetting.PhononTts(name="Voice A",modelPath="/local/phonon",voice="claire")
        val kokoro: TTSProviderSetting = TTSProviderSetting.KokoroTts(name="Voice B",modelPath="/local/kokoro",voice="jf_alpha")
        val json = Json { classDiscriminator = "type" }
        val one = json.decodeFromString<TTSProviderSetting>(json.encodeToString<TTSProviderSetting>(phonon))
        val two = json.decodeFromString<TTSProviderSetting>(json.encodeToString<TTSProviderSetting>(kokoro))
        assertEquals(phonon, one)
        assertEquals(kokoro, two)
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.PhononTts::class))
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.KokoroTts::class))
    }
}
