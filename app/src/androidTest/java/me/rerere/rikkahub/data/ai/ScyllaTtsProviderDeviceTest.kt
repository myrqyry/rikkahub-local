package me.rerere.rikkahub.data.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.scylla.ScyllaTtsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit real-device acceptance. Bundle must be independently staged and verified.
 * Run by class via adb instrument, never Gradle connectedDebugAndroidTest on user data.
 */
@RunWith(AndroidJUnit4::class)
class ScyllaTtsProviderDeviceTest {
    @Test fun scyllaManagerGeneratesActualPcmWithoutDisplacingOtherProviders(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val dir = File(context.getExternalFilesDir(null), "local-models/tts-scyllasband")
        val invalid = ScyllaTtsCatalog.verify(dir)
        assertTrue("Scylla download must be verified on the device: $invalid", invalid.isEmpty())
        val manager = TTSManager(context)
        val setting = TTSProviderSetting.ScyllaTts(
            modelPath = dir.absolutePath,
            voice = "scylla",
            language = "en_us",
            energy = 2.2f,
            valence = 2.1f,
            seed = 31415L,
        )
        assertTrue(manager.reusesEngine(setting))
        try {
            val chunks = withTimeout(180_000L) {
                manager.generateSpeech(
                    setting, TTSRequest("Hello there. This is an expressive local voice in RikkaHub."),
                ).toList()
            }
            assertTrue("Native runtime must produce actual chunks", chunks.isNotEmpty())
            assertEquals(1, chunks.count { it.isLast })
            assertTrue(chunks.all { it.format == AudioFormat.PCM && it.sampleRate == 24000 })
            val samples = chunks.sumOf { it.data.size / 2 }
            assertTrue("Must generate at least one second of audio", samples > 24000)
            var peak = 0f
            var nonSilent = 0
            for (chunk in chunks) {
                val buffer = ByteBuffer.wrap(chunk.data).order(ByteOrder.LITTLE_ENDIAN)
                while (buffer.hasRemaining()) {
                    val v = abs(buffer.short.toInt()) / 32768f
                    peak = maxOf(peak, v)
                    if (v > 0.0001f) nonSilent++
                }
            }
            Log.i(TAG, "RESULT chunks=${chunks.size} samples=$samples peak=$peak nonSilent=$nonSilent")
            assertTrue("Audio must not be silent", nonSilent > samples / 100)
            assertTrue("Audible waveform required", peak > 0.001f)

            // Lifecycle regression: release the first voice and request another
            // voice/accent in a fresh session. No stale native engine reuse.
            manager.onSessionEnd()
            val second = setting.copy(
                voice = "ink", language = "en_gb", seed = 2718L,
                speed = 1.2f, steps = 4, sampler = "euler",
            )
            val newChunks = withTimeout(180_000L) {
                manager.generateSpeech(
                    second, TTSRequest("A second voice should work after stopping the first."),
                ).toList()
            }
            assertTrue("Second speaker must synthesize", newChunks.isNotEmpty())
            assertEquals(1, newChunks.count { it.isLast })
            assertTrue(newChunks.sumOf { it.data.size } > 48000)
            val fastBytes = newChunks.sumOf { it.data.size }
            Log.i(TAG, "SECOND_VOICE chunks=${newChunks.size} bytes=$fastBytes speed=1.2 sampler=euler steps=4")

            // Same voice/text/seed at the model's normal pace and Heun sampler.
            // Verify the settings reach the native duration predictor, not just serialization.
            manager.onSessionEnd()
            val normalEuler = second.copy(speed = 1f)
            val normalChunks = withTimeout(180_000L) {
                manager.generateSpeech(
                    normalEuler, TTSRequest("A second voice should work after stopping the first."),
                ).toList()
            }
            assertTrue("Euler at normal pace should generate speech", normalChunks.isNotEmpty())
            assertEquals(1, normalChunks.count { it.isLast })
            val normalBytes = normalChunks.sumOf { it.data.size }
            assertTrue("Changing only speed should shorten speech: normal=$normalBytes fast=$fastBytes",
                normalBytes > fastBytes)
            Log.i(TAG, "NORMAL_EULER bytes=$normalBytes speed=1 steps=4")

            // Switch only the sampler to Heun, with the same speed, seed and text.
            manager.onSessionEnd()
            val heun = normalEuler.copy(sampler = "heun")
            val heunChunks = withTimeout(180_000L) {
                manager.generateSpeech(
                    heun, TTSRequest("A second voice should work after stopping the first."),
                ).toList()
            }
            assertTrue("Heun must synthesize real audio", heunChunks.isNotEmpty())
            assertEquals(1, heunChunks.count { it.isLast })
            assertTrue("Heun PCM must be nonempty", heunChunks.sumOf { it.data.size } > 48000)
            Log.i(TAG, "HEUN_VOICE bytes=${heunChunks.sumOf { it.data.size }} speed=1 steps=4")
        } finally {
            manager.onSessionEnd()
        }
    }
    companion object { private const val TAG = "ScyllaRikkaDeviceAcceptance" }
}
