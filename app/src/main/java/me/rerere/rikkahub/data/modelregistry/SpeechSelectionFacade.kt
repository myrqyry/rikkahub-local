package me.rerere.rikkahub.data.modelregistry

import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.tts.provider.TTSProviderSetting

sealed interface SpeechProviderResolution<out T> {
    val provider: T?

    data class Available<T>(override val provider: T) : SpeechProviderResolution<T>

    data object Unavailable : SpeechProviderResolution<Nothing> {
        override val provider: Nothing? = null
    }
}

object SpeechSelectionFacade {
    fun resolveTtsProvider(
        assistant: Assistant,
        settings: Settings,
    ): SpeechProviderResolution<TTSProviderSetting> {
        val provider = assistant.ttsProviderOverrideId
            ?.let { id -> settings.ttsProviders.firstOrNull { it.id == id } }
            ?: settings.getSelectedTTSProvider()
        return provider?.let { SpeechProviderResolution.Available(it) }
            ?: SpeechProviderResolution.Unavailable
    }

    fun resolveAsrProvider(
        assistant: Assistant,
        settings: Settings,
    ): SpeechProviderResolution<ASRProviderSetting> {
        val provider = assistant.asrProviderOverrideId
            ?.let { id -> settings.asrProviders.firstOrNull { it.id == id } }
            ?: settings.getSelectedASRProvider()
        return provider?.let { SpeechProviderResolution.Available(it) }
            ?: SpeechProviderResolution.Unavailable
    }
}
