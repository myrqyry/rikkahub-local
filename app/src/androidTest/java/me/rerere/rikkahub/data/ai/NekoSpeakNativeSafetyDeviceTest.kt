package me.rerere.rikkahub.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.providers.NekoSpeakTTSProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Manual real-device regression: the app must survive an unbundled NekoSpeak JNI backend. */
@RunWith(AndroidJUnit4::class)
class NekoSpeakNativeSafetyDeviceTest {
    @Test
    fun absentNativeLibraryReportsARecoverableError() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val available = NekoSpeakTTSProvider.isNativeBackendAvailable()
        assumeFalse("Only tests builds without a packaged NekoSpeak JNI backend", available)

        val error = runCatching {
            NekoSpeakTTSProvider().generateSpeech(
                context,
                TTSProviderSetting.NekoSpeakTts(modelPath = "/missing/speech_model.ort"),
                TTSRequest("test"),
            ).single()
        }.exceptionOrNull()

        assertTrue("No usable native engine must throw a regular exception", error is IllegalStateException)
        assertTrue("Error must explain the native limitation", error?.message.orEmpty().contains("native"))
        assertFalse("Missing native engine must remain detectable", NekoSpeakTTSProvider.isNativeBackendAvailable())
    }
}
