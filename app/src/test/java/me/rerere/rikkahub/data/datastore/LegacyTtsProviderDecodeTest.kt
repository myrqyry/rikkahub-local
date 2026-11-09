package me.rerere.rikkahub.data.datastore

import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTtsProviderDecodeTest {
    private val legacyConfig = """
        [
          {"type":"system","id":"026a01a2-c3a0-4fd5-8075-80e03bdef200","name":"Android"},
          {"type":"nekospeak","id":"11111111-1111-1111-1111-111111111111","name":"NekoSpeak TTS","modelPath":"/some/model.ort"},
          {"type":"openai","id":"22222222-2222-2222-2222-222222222222","name":"Custom voice","model":"gpt-4o-mini-tts"}
        ]
    """.trimIndent()

    @Test
    fun `legacy NekoSpeak is ignored without discarding other voices`() {
        val restored = decodeTtsProvidersTolerant(legacyConfig)
        assertEquals(listOf("Android", "Custom voice"), restored.map { it.name })
        assertTrue(restored[0] is TTSProviderSetting.SystemTTS)
        assertTrue(restored[1] is TTSProviderSetting.OpenAI)
    }

    @Test
    fun `other unsupported provider variants are isolated per entry`() {
        val saved = legacyConfig.replace("\"nekospeak\"", "\"unavailable-format\"")
        val restored = decodeTtsProvidersTolerant(saved)
        assertEquals(listOf("Android", "Custom voice"), restored.map { it.name })
    }

    @Test
    fun `only disabled providers yield empty list so system recovery can apply`() {
        val onlyLegacy = """[{"type":"nekospeak","name":"Old voice"}]"""
        val restored = decodeTtsProvidersTolerant(onlyLegacy)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `renamed active OpenAI voice is not discarded by name`() {
        val active = """[{"type":"openai","name":"NekoSpeak"}]"""
        val restored = decodeTtsProvidersTolerant(active)
        assertEquals(1, restored.size)
        assertTrue(restored.single() is TTSProviderSetting.OpenAI)
    }
}
