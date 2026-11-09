package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Matcha waveform diagnostic on installed model. No audio playback, provider
 * selection or preferences changes. Launch with explicit class via adb instrument.
 */
@RunWith(AndroidJUnit4::class)
class MatchaTtsWaveformDeviceTest {
    @Test
    fun installedMatchaActuallyProducesNonSilentPcm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = File(context.filesDir, "local-models/tts-matcha-tts")
        assertTrue("Matcha bundle not installed: $model", model.isDirectory)
        val manager = TTSManager(context)
        val setting = TTSProviderSetting.MatchaTts(
            modelPath = model.absolutePath,
            flowSteps = 4, seed = 7L,
        )
        val started = System.nanoTime()
        try {
            val chunks = manager.generateSpeech(
                setting,
                TTSRequest("Hello there. This is a test of the local Matcha speech engine."),
            ).toList()
            val wallMs = (System.nanoTime() - started) / 1_000_000
            val pcm = chunks.filter { it.format == AudioFormat.PCM && it.sampleRate == 22050 }
            assertTrue("Longer sentence must stream multiple short Matcha chunks", pcm.size >= 2)
            assertTrue("Only final chunk may end the PCM stream", pcm.dropLast(1).all { !it.isLast })
            assertTrue("Last chunk must end the PCM stream", pcm.last().isLast)
            assertTrue("Expected 22.05kHz PCM", pcm.isNotEmpty())
            val samples = pcm.sumOf { it.data.size / 2 }
            var peak = 0f
            var sum = 0.0
            var nonzero = 0
            var nonfinite = 0
            for (chunk in pcm) {
                val buf = ByteBuffer.wrap(chunk.data).order(ByteOrder.LITTLE_ENDIAN)
                while (buf.remaining() >= 2) {
                    val v = abs(buf.short.toInt()) / 32768f
                    peak = maxOf(peak, v)
                    sum += v * v
                    if (v > 0.001f) nonzero++
                    if (!v.isFinite()) nonfinite++
                }
            }
            val rms = kotlin.math.sqrt(sum / maxOf(1, samples))
            Log.i("MatchaRealDevice", "RESULT wallMs=$wallMs chunks=${chunks.size} samples=$samples peak=$peak rms=$rms nonzero=$nonzero nonfinite=$nonfinite")
            assertTrue("No valid audio samples produced", samples > 22050)
            assertTrue("Waveform is silent or nearly silent: peak=$peak rms=$rms", peak > 0.005f && rms > 0.0005)
        } finally {
            manager.onSessionEnd()
        }
    }
}
