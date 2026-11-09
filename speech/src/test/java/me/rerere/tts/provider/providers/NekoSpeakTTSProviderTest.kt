package me.rerere.tts.provider.providers

import android.content.ContextWrapper
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NekoSpeakTTSProviderTest {
    @Test fun absentNativeBridgeReturnsARecoverableErrorInsteadOfLinkageCrash() {
        val err = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                NekoSpeakTTSProvider().generateSpeech(
                    ContextWrapper(null),
                    TTSProviderSetting.NekoSpeakTts(modelPath = "/nonexistent/model.ort"),
                    TTSRequest("test"),
                ).single()
            }
        }
        assertTrue(err.message.orEmpty().contains("NekoSpeak"))
        assertTrue(err.message.orEmpty().contains("native", ignoreCase = true))
    }

    @Test fun emptyModelPathIsRejectedBeforeNativeSynthesis() {
        val err = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                NekoSpeakTTSProvider().generateSpeech(
                    ContextWrapper(null),
                    TTSProviderSetting.NekoSpeakTts(modelPath = ""),
                    TTSRequest("test"),
                ).single()
            }
        }
        assertTrue(err.message.orEmpty().contains("model", ignoreCase = true))
    }
}
