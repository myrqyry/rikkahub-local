package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.rerere.tts.kitten.KittenTtsBundle
import me.rerere.tts.kitten.KittenTtsConfig
import me.rerere.tts.kitten.KittenTtsEngine
import me.rerere.tts.kitten.KittenTtsTokenizer
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fully local Kitten TTS Nano 0.1 — ultra-lightweight (15 M params, <25 MB),
 * CPU-only, 8 premium voices, 24 kHz output.
 *
 * Bundle: KittenML/kitten-tts-nano-0.1 — Apache 2.0
 * Files: kitten_tts_nano_v0_1.onnx + voices.npz (+ optional config.json)
 *
 * One-shot synthesis (no streaming): tokenize → style lookup → ONNX run.
 * Float32 waveform converted to PCM16 for the audio pipeline.
 */
class KittenTTSProvider : TTSProvider<TTSProviderSetting.KittenTts> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.KittenTts,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val directory = File(providerSetting.modelPath)
        require(directory.isDirectory) {
            "Kitten TTS model dir does not exist: ${directory.absolutePath}. " +
                "Place kitten_tts_nano_v0_1.onnx + voices.npz in the folder or download from ${me.rerere.tts.kitten.KittenTtsCatalog.ENTRIES.first().sourceUrl}"
        }

        KittenTtsBundle.open(directory).use { bundle ->
            val engine = KittenTtsEngine.create(
                bundle = bundle,
                config = KittenTtsConfig(
                    voice = providerSetting.voice,
                    speed = providerSetting.speed,
                ),
            )

            val tokenizer = KittenTtsTokenizer()
            val text = request.text.trim()
            if (text.isEmpty()) {
                emit(emptyLastChunk())
                return@flow
            }

            val ids = tokenizer.encode(text)
            if (ids.isEmpty()) {
                emit(emptyLastChunk())
                return@flow
            }

            val style = bundle.voices[providerSetting.voice]
                ?: bundle.voices.values.firstOrNull()
                ?: throw IllegalStateException("No voices available in bundle")

            val waveform = engine.runInference(
                inputIds = ids,
                style = style,
                speed = providerSetting.speed,
            )

            val pcmBytes = FloatArrayToPcm16(waveform)
            emit(
                AudioChunk(
                    data = pcmBytes,
                    format = AudioFormat.PCM,
                    sampleRate = 24000,
                    isLast = true,
                    metadata = mapOf(
                        "provider" to "kitten-tts",
                        "voice" to providerSetting.voice,
                    ),
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun FloatArrayToPcm16(waveform: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(waveform.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in waveform) {
            buf.putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        return buf.array()
    }

    private fun emptyLastChunk() = AudioChunk(
        data = ByteArray(0),
        format = AudioFormat.PCM,
        sampleRate = 24000,
        isLast = true,
        metadata = mapOf("provider" to "kitten-tts"),
    )
}
