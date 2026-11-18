package me.rerere.tts.provider.providers

import android.content.ContextWrapper
import java.io.File
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit network/ffmpeg opt-in; ordinary CI does not call the community proxy. */
class TikTokLiveAcceptanceTest {
    @Test fun actualCloudProviderReturnsDecodableMp3() {
        assumeTrue("Set RIKKAHUB_TIKTOK_ACCEPTANCE=1", System.getenv("RIKKAHUB_TIKTOK_ACCEPTANCE") == "1")
        for (voice in listOf("en_us_001", "en_us_stitch")) {
            val result = runBlocking {
                TikTokTTSProvider().generateSpeech(
                    ContextWrapper(null), TTSProviderSetting.TikTok(voice = voice),
                    TTSRequest("Hello from RikkaHub."),
                ).single()
            }
            assertEquals(AudioFormat.MP3, result.format)
            assertEquals(voice, result.metadata["voice"])
            val input = File.createTempFile("rikka-tiktok-", ".mp3")
            val output = File.createTempFile("rikka-tiktok-", ".pcm")
            try {
                input.writeBytes(result.data)
                val decode = ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-y",
                    "-i", input.absolutePath, "-ac", "1", "-ar", "24000", "-f", "s16le", output.absolutePath)
                    .redirectErrorStream(true).start()
                val diagnostics = decode.inputStream.bufferedReader().readText()
                assertEquals(diagnostics, 0, decode.waitFor())
                val pcm = output.readBytes()
                assertTrue(pcm.size > 4800 && pcm.any { it.toInt() != 0 })
                println("TikTok live: $voice, ${result.data.size} MP3 bytes, ${pcm.size / 48000.0}s decoded")
            } finally {
                input.delete()
                output.delete()
            }
        }
    }
}
