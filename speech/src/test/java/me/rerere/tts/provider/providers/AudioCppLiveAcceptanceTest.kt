package me.rerere.tts.provider.providers

import android.content.ContextWrapper
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises the production OkHttp adapter against a real audio.cpp server.
 * Opt in with RIKKAHUB_AUDIOCPP_ACCEPTANCE=1; normal CI has no live server.
 */
class AudioCppLiveAcceptanceTest {
    @Test fun realServerProducesPlayableWav() {
        assumeTrue("Explicit real-server opt-in required", System.getenv("RIKKAHUB_AUDIOCPP_ACCEPTANCE") == "1")
        val base = System.getenv("RIKKAHUB_AUDIOCPP_URL") ?: "http://127.0.0.1:8767"
        val model = System.getenv("RIKKAHUB_AUDIOCPP_MODEL") ?: "kokoro"
        val voice = System.getenv("RIKKAHUB_AUDIOCPP_VOICE") ?: "af_heart"
        val result = runBlocking {
            AudioCppTTSProvider().generateSpeech(
                ContextWrapper(null),
                TTSProviderSetting.AudioCpp(baseUrl = base, model = model, voice = voice),
                TTSRequest("The audio dot cpp speech provider is working."),
            ).single()
        }
        assertEquals(AudioFormat.WAV, result.format)
        assertTrue(result.isLast)
        assertTrue("Expected a real waveform, not a header-only response", result.data.size > 10_000)
        assertTrue(result.data.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()))
        assertTrue(result.data.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()))
        assertEquals("audio.cpp", result.metadata["provider"])
    }
}
