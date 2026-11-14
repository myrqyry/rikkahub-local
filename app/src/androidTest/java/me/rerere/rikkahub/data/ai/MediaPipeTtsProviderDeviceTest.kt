package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual production provider and streaming PCM on a physical Pixel; no speaker output or settings changes. */
@RunWith(AndroidJUnit4::class)
class MediaPipeTtsProviderDeviceTest {
    private fun checkProductionPath(provider: TTSProviderSetting, label: String) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = TTSManager(context)
        val start = System.nanoTime()
        try {
            manager.onSessionStart(provider)
            val chunks = manager.generateSpeech(
                provider, TTSRequest("Hello from the Pixel. This is real local speech."),
            ).toList()
            val pcm = chunks.filter { it.format == AudioFormat.PCM }
            assertTrue("No PCM chunks from $label", pcm.isNotEmpty())
            assertTrue("Nonfinal chunks must precede final", pcm.dropLast(1).none { it.isLast })
            assertTrue("Missing final chunk from $label", pcm.last().isLast)
            assertTrue("Wrong sample rate", pcm.all { it.sampleRate == 24000 })
            assertEquals("Only one end marker expected", 1, pcm.count { it.isLast })
            var sum = 0.0
            var peak = 0f
            var samples = 0
            pcm.forEach { chunk ->
                val b = ByteBuffer.wrap(chunk.data).order(ByteOrder.LITTLE_ENDIAN)
                assertTrue("Odd PCM byte count", chunk.data.size % 2 == 0)
                while (b.remaining() >= 2) {
                    val value = abs(b.short.toInt()) / 32768f
                    sum += value * value
                    peak = maxOf(peak, value)
                    samples++
                }
            }
            val rms = sqrt(sum / samples.coerceAtLeast(1))
            val millis = (System.nanoTime() - start) / 1_000_000
            Log.i("MediaPipeProvider", "RESULT $label millis=$millis chunks=${chunks.size} samples=$samples peak=$peak rms=$rms")
            assertTrue("Audio is empty: $label", samples > 24000)
            assertTrue("Audio is silent: $label", peak > 0.005f && rms > 0.0005)
        } finally { manager.onSessionEnd() }
    }

    @Test
    fun phononProductionProviderStreamsPCM() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "local-models/tts-mediapipe-phonon")
        checkProductionPath(TTSProviderSetting.PhononTts(modelPath=dir.absolutePath), "phonon")
    }

    @Test
    fun kokoroProductionProviderStreamsPCM() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "local-models/tts-mediapipe-kokoro")
        checkProductionPath(TTSProviderSetting.KokoroTts(modelPath=dir.absolutePath), "kokoro")
    }
}
