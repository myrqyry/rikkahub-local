package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SpeechProviderActionsTest {
    @Test
    fun `built in system TTS cannot be removed`() {
        assertFalse(canRemoveTtsSpeechProvider(DEFAULT_SYSTEM_TTS_ID))
    }

    @Test
    fun `custom TTS providers are removable after confirmation`() {
        assertTrue(canRemoveTtsSpeechProvider(Uuid.parse("00000000-0000-0000-0000-000000000222")))
    }

    @Test
    fun `unavailable NekoSpeak cannot be selected while other providers remain usable`() {
        assertFalse(canSelectTtsSpeechProvider(
            me.rerere.tts.provider.TTSProviderSetting.NekoSpeakTts(), nekoNativeAvailable = false,
        ))
        assertTrue(canSelectTtsSpeechProvider(
            me.rerere.tts.provider.TTSProviderSetting.NekoSpeakTts(), nekoNativeAvailable = true,
        ))
        assertTrue(canSelectTtsSpeechProvider(
            me.rerere.tts.provider.TTSProviderSetting.SystemTTS(), nekoNativeAvailable = false,
        ))
    }
}
