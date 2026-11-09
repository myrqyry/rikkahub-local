package me.rerere.tts.controller

import me.rerere.tts.effects.VoiceEffectPreset
import me.rerere.tts.provider.TTSProviderSetting

/**
 * Keep complete-buffer playback when a voice effect needs the full waveform.
 * Matcha now produces short sequential PCM segments and can start playback
 * before a long parent chat chunk has finished synthesis.
 */
internal fun shouldPlayPcmStream(
    provider: TTSProviderSetting,
    effect: VoiceEffectPreset,
): Boolean = effect == VoiceEffectPreset.OFF &&
    (provider is TTSProviderSetting.PocketTts || provider is TTSProviderSetting.MatchaTts)
