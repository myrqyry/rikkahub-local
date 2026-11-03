package me.rerere.tts.provider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSProviderSettingAudioCppTest {
    @Test fun selfHostedProviderIsExplicitAndRegistered() {
        val setting = TTSProviderSetting.AudioCpp()
        assertEquals("", setting.baseUrl)
        assertEquals("", setting.model)
        assertEquals("", setting.voice)
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.AudioCpp::class))
    }

    @Test fun persistsWithoutChangingOtherProviderTypes() {
        val setting: TTSProviderSetting = TTSProviderSetting.AudioCpp(
            baseUrl = "http://doopa:8080", model = "kokoro", voice = "af_heart",
        )
        val json = Json.encodeToString(setting)
        assertTrue(json.contains("audio-cpp"))
        val roundTrip = Json.decodeFromString<TTSProviderSetting>(json) as TTSProviderSetting.AudioCpp
        assertEquals(setting, roundTrip)
        assertEquals("kokoro", roundTrip.model)
        assertFalse(roundTrip.copyProvider(name = "Home speech").name.isBlank())
    }
}
