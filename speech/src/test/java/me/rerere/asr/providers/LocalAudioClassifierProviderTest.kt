package me.rerere.asr.providers

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioClassifierProviderTest {
    @Test
    fun `empty model path reports error, not crash`() = runBlocking {
        val controller = LocalAudioClassifierController(
            appContext = ContextWrapper(null),
            provider = ASRProviderSetting.LocalAudioClassifier(modelPath = ""),
        )
        controller.start { }
        assertEquals(ASRStatus.Error, controller.state.value.status)
        assertTrue(controller.state.value.errorMessage?.contains("model", ignoreCase = true) == true)
        controller.dispose()
    }

    @Test
    fun `provider type is registered in Types`() {
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.LocalAudioClassifier::class))
    }

    @Test
    fun `serializes to expected serial name`() {
        val json = kotlinx.serialization.json.Json.encodeToString(
            ASRProviderSetting.serializer(),
            ASRProviderSetting.LocalAudioClassifier(),
        )
        assertTrue(json.contains("\"local_audio_classifier\""))
    }
}
