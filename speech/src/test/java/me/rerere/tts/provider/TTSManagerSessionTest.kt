package me.rerere.tts.provider

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSManagerSessionTest {
    private fun manager() = TTSManager(ContextWrapper(null) as Context)

    @Test
    fun reusesEngineFlagsLocalProvidersOnly() {
        val m = manager()
        assertTrue(m.reusesEngine(TTSProviderSetting.Qwen3Tts(modelPath = "/x")))
        assertTrue(m.reusesEngine(TTSProviderSetting.PocketTts(modelPath = "/x")))
        assertTrue(m.reusesEngine(TTSProviderSetting.KittenTts(modelPath = "/x")))
        assertFalse(m.reusesEngine(TTSProviderSetting.OpenAI()))
    }

    @Test
    fun sessionStartAndEndAreSafeForLocalProvidersWithoutModelFiles() {
        val m = manager()
        m.onSessionStart(TTSProviderSetting.Qwen3Tts(modelPath = "/nonexistent"))
        m.onSessionStart(TTSProviderSetting.PocketTts(modelPath = "/nonexistent"))
        m.onSessionStart(TTSProviderSetting.KittenTts(modelPath = "/nonexistent"))
        m.onSessionEnd()
        m.onSessionEnd()
    }

    @Test
    fun sessionStartAndEndAreSafeForCloudProviders() {
        val m = manager()
        m.onSessionStart(TTSProviderSetting.OpenAI())
        m.onSessionEnd()
    }
}
