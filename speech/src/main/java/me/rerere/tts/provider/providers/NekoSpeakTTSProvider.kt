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
import java.io.File

class NekoSpeakTTSProvider : TTSProvider<TTSProviderSetting.NekoSpeakTts> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.NekoSpeakTts,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        // This provider historically called an external JNI method without loading
        // its library. The Android build currently ships no libnekospeak.so at all:
        // invoking nativeSynthesize therefore throws an uncaught UnsatisfiedLinkError.
        // Fail with a normal, actionable Exception before reaching that path.
        if (providerSetting.modelPath.isBlank()) {
            throw IllegalStateException("NekoSpeak model file is not configured")
        }

        val pcmData = withContext(Dispatchers.IO) {
            ensureLoaded()
            check(File(providerSetting.modelPath).isFile) {
                "NekoSpeak model file does not exist: ${providerSetting.modelPath}"
            }
            try {
                nativeSynthesize(
                    modelPath = providerSetting.modelPath,
                    text = request.text,
                    voice = providerSetting.voice,
                    speed = providerSetting.speed,
                    pitch = providerSetting.pitch,
                )
            } catch (error: UnsatisfiedLinkError) {
                throw IllegalStateException(
                    "NekoSpeak native synthesis is unavailable in this app build",
                    error,
                )
            }
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
        @Volatile private var loaded = false

        /** A nonfatal capability query for settings UI; no model is loaded or synthesized. */
        fun isNativeBackendAvailable(): Boolean = try {
            ensureLoaded()
            true
        } catch (_: IllegalStateException) {
            false
        }

        @Synchronized
        fun ensureLoaded() {
            if (!loaded) {
                try {
                    System.loadLibrary("nekospeak")
                } catch (error: UnsatisfiedLinkError) {
                    throw IllegalStateException(
                        "NekoSpeak native engine is not bundled in this app build",
                        error,
                    )
                }
                loaded = true
            }
        }
    }
}
