package me.rerere.tts.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSProviderSettingMatchaTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun matcha_is_registered_with_expected_defaults() {
        val setting = TTSProviderSetting.MatchaTts()

        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.MatchaTts::class))
        assertEquals("Matcha TTS (Local)", setting.name)
        assertEquals("", setting.modelPath)
        assertEquals(1.0f, setting.speechSpeed)
        assertEquals(1.0f, setting.durationScale)
        assertEquals(10, setting.flowSteps)
        assertEquals(null, setting.seed)
    }

    @Test
    fun matcha_round_trips_serialization() {
        val original = TTSProviderSetting.MatchaTts(
            modelPath = "/models/matcha",
            speechSpeed = 1.25f,
            durationScale = 0.8f,
            flowSteps = 14,
            seed = 42L,
        )

        val encoded = json.encodeToString(TTSProviderSetting.serializer(), original)
        val decoded = json.decodeFromString<TTSProviderSetting>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun copyProvider_changes_only_identity_fields() {
        val original = TTSProviderSetting.MatchaTts(seed = 42L)
        val copied = original.copyProvider(name = "My Matcha") as TTSProviderSetting.MatchaTts

        assertEquals(original.id, copied.id)
        assertEquals("My Matcha", copied.name)
        assertEquals(original.seed, copied.seed)
    }
}
