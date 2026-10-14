package me.rerere.rikkahub.rvc

import me.rerere.tts.rvc.RvcFilterSettings
import me.rerere.tts.rvc.RvcInputAudio
import me.rerere.tts.rvc.VoxaRvcClient

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit opt-in, not a fake CI success. Before running:
 *   adb reverse tcp:8000 tcp:8000
 *   ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.rvcLive=true
 *
 * Voxa is intentionally loopback-only on the desktop; this does not publish it.
 */
@RunWith(AndroidJUnit4::class)
class RvcLiveDeviceTest {
    @Test fun localVoxaRoundTripFromAndroid() = runBlocking {
        assumeTrue("Requires an operator-approved local Voxa server over adb reverse",
            InstrumentationRegistry.getArguments().getString("rvcLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = VoxaRvcClient()
        val origin = "http://127.0.0.1:8000"
        val models = client.listModels(origin)
        assertTrue("Expected real model catalogue", models.isNotEmpty())
        val original = InstrumentationRegistry.getInstrumentation().context.assets.open("rvc-smoke.wav").use { it.readBytes() }
        RvcInputAudio.inspectWav(original)
        val converted = client.convert(
            context,
            TTSResponse(original, AudioFormat.WAV),
            RvcFilterSettings(enabled = true, endpoint = origin, model = models.first(), transpose = 0),
        )
        assertEquals(AudioFormat.WAV, converted.format)
        assertEquals("voxa-rvc", converted.metadata["effect"])
        assertTrue(converted.audioData.size > 44)
        RvcInputAudio.inspectWav(converted.audioData)
    }
}
