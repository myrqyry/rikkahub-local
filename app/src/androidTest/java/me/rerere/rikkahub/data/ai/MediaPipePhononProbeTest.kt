package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

/** Real Android engine probe: no provider configuration changes or speaker playback. */
@RunWith(AndroidJUnit4::class)
class MediaPipePhononProbeTest {
    @Test
    fun kokoroGeneratesNonSilentPcmOnPixel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "local-models/tts-mediapipe-kokoro/kokoro_int8_fp16.litertlm")
        assertTrue("Kokoro bundle not installed: $file", file.isFile)
        val start = System.nanoTime()
        val engine = TextToSpeech.create(TextToSpeechOptions(
            baseOptions = BaseOptions.builder().setModelAssetPath(file.absolutePath).build()
        ))
        try {
            val voices = engine.listVoices()
            Log.i("KokoroProbe", "voices=${voices.size}, first=${voices.take(5).map { it.voiceId }}")
            val result = engine.synthesize(
                "Hello. This is Kokoro speaking from the Pixel.",
                SynthesisOptions(voiceId = "af_heart"),
            )
            val ms = (System.nanoTime() - start) / 1_000_000
            val samples = result.samples
            val peak = samples.maxOfOrNull { abs(it) } ?: 0f
            val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size.coerceAtLeast(1))
            Log.i("KokoroProbe", "RESULT wallMs=$ms rate=${result.sampleRateHz} samples=${samples.size} peak=$peak rms=$rms")
            assertTrue("No Kokoro voices", voices.any { it.voiceId == "af_heart" })
            assertTrue("Wrong sample rate", result.sampleRateHz == 24000)
            assertTrue("No speech frames", samples.size > 24000)
            assertTrue("Silent", peak > 0.01f && rms > 0.001)
        } finally { engine.close() }
    }

    @Test
    fun phononGeneratesNonSilentPcmOnPixel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "local-models/tts-mediapipe-phonon/phonon_int8.litertlm")
        assertTrue("Phonon bundle not installed: $file", file.isFile)
        val start = System.nanoTime()
        val engine = TextToSpeech.create(
            TextToSpeechOptions(baseOptions = BaseOptions.builder()
                .setModelAssetPath(file.absolutePath).build())
        )
        try {
            val voices = engine.listVoices()
            Log.i("PhononProbe", "voices=${voices.map { it.voiceId }}")
            val audio = engine.synthesize("Hello from the Pixel. This is real local speech.")
            val ms = (System.nanoTime() - start) / 1_000_000
            val samples = audio.samples
            val peak = samples.maxOfOrNull { abs(it) } ?: 0f
            val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size.coerceAtLeast(1))
            Log.i("PhononProbe", "RESULT wallMs=$ms rate=${audio.sampleRateHz} samples=${samples.size} peak=$peak rms=$rms")
            assertTrue("No voices", voices.isNotEmpty())
            assertTrue("Wrong sample rate", audio.sampleRateHz == 24000)
            assertTrue("No speech frames", samples.size > 24000)
            assertTrue("Silent", peak > 0.01f && rms > 0.001)
        } finally { engine.close() }
    }
}
