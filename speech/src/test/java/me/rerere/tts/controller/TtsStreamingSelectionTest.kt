package me.rerere.tts.controller

import me.rerere.tts.effects.VoiceEffectPreset
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsStreamingSelectionTest {
    @Test fun `local Matcha can start PCM playback before entire utterance is synthesized`() {
        assertTrue(shouldPlayPcmStream(TTSProviderSetting.MatchaTts(), VoiceEffectPreset.OFF))
    }

    @Test fun `Phonon and Kokoro stream their native PCM without full-buffer delay`() {
        assertTrue(shouldPlayPcmStream(TTSProviderSetting.PhononTts(), VoiceEffectPreset.OFF))
        assertTrue(shouldPlayPcmStream(TTSProviderSetting.KokoroTts(), VoiceEffectPreset.OFF))
        assertFalse(shouldPlayPcmStream(TTSProviderSetting.KokoroTts(), VoiceEffectPreset.TELEPHONE))
    }

    @Test fun `Pocket streaming remains enabled`() {
        assertTrue(shouldPlayPcmStream(TTSProviderSetting.PocketTts(), VoiceEffectPreset.OFF))
    }

    @Test fun `effects preserve full waveform processing instead of streaming`() {
        assertFalse(shouldPlayPcmStream(TTSProviderSetting.MatchaTts(), VoiceEffectPreset.TELEPHONE))
        assertFalse(shouldPlayPcmStream(TTSProviderSetting.PocketTts(), VoiceEffectPreset.TELEPHONE))
    }

    @Test fun `other providers retain their existing playback path`() {
        assertFalse(shouldPlayPcmStream(TTSProviderSetting.SystemTTS(), VoiceEffectPreset.OFF))
    }
}
