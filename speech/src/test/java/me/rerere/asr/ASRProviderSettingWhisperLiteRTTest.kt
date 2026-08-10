package me.rerere.asr

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ASRProviderSettingWhisperLiteRTTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaults_to_portable_base_model() {
        val setting = ASRProviderSetting.WhisperLiteRT()

        assertEquals("whisper-base", setting.modelId)
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.WhisperLiteRT::class))
    }

    @Test
    fun model_id_round_trips() {
        val original = ASRProviderSetting.WhisperLiteRT(modelId = "whisper-tiny")

        val encoded = json.encodeToString(ASRProviderSetting.serializer(), original)
        val decoded = json.decodeFromString<ASRProviderSetting>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun legacy_settings_use_base_model_default() {
        val legacy = """
            {"type":"whisper_litert","id":"00000000-0000-0000-0000-000000000001", "name":"Whisper LiteRT", "modelPath":"/models/whisper.tflite", "language":"en", "sampleRate":16000}
        """.trimIndent()

        val decoded = json.decodeFromString<ASRProviderSetting>(legacy)

        assertEquals("whisper-base", (decoded as ASRProviderSetting.WhisperLiteRT).modelId)
    }
}
