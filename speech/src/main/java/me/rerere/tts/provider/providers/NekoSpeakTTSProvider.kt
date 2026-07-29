package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

class NekoSpeakTTSProvider : TTSProvider<TTSProviderSetting.NekoSpeakTts> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.NekoSpeakTts,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val pcmData = withContext(Dispatchers.IO) {
            nativeSynthesize(
                modelPath = providerSetting.modelPath,
                text = request.text,
                voice = providerSetting.voice,
                speed = providerSetting.speed,
                pitch = providerSetting.pitch,
            )
        }
        emit(
            AudioChunk(
                data = pcmData,
                format = AudioFormat.PCM,
                sampleRate = 24000,
                isLast = true,
                metadata = mapOf(
                    "provider" to "nekospeak",
                    "voice" to providerSetting.voice,
                )
            )
        )
    }

    private external fun nativeSynthesize(
        modelPath: String,
        text: String,
        voice: String,
        speed: Float,
        pitch: Float,
    ): ByteArray

    companion object {
        private var loaded = false
        fun ensureLoaded() {
            if (!loaded) {
                System.loadLibrary("nekospeak")
                loaded = true
            }
        }
    }
}