package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.tts.controller.AudioPlayer
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in device acceptance: real cloud request and native ExoPlayer output; no saved settings changed. */
@RunWith(AndroidJUnit4::class)
class TikTokTtsDeviceTest {
    @Test
    fun realCloudSpeechPlaysToCompletion(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = TTSManager(context)
        val setting = TTSProviderSetting.TikTok(voice = "en_us_stitch")
        val chunk = withTimeout(30000) {
            manager.generateSpeech(setting, TTSRequest("Hello from RikkaHub. TikTok speech is working.")).single()
        }
        assertEquals(AudioFormat.MP3, chunk.format)
        assertTrue(chunk.isLast)
        assertEquals("en_us_stitch", chunk.metadata["voice"])
        assertTrue(chunk.data.size > 1000)
        withContext(Dispatchers.Main) {
            val player = AudioPlayer(context)
            try {
                withTimeout(30000) {
                    player.play(TTSResponse(audioData = chunk.data, format = chunk.format))
                }
                val receipt = player.playbackState.first { it.status == PlaybackStatus.Ended }
                assertTrue("No decoded audio duration", receipt.durationMs > 500)
                assertTrue("Playback did not advance", receipt.positionMs >= receipt.durationMs)
                Log.i("TikTokDeviceTest", "RESULT bytes=${chunk.data.size} durationMs=${receipt.durationMs} positionMs=${receipt.positionMs} status=${receipt.status}")
            } finally {
                player.release()
            }
        }
    }
}
