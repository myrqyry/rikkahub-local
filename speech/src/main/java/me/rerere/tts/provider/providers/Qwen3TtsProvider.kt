package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.qwen3.Qwen3TtsEngine
import me.rerere.tts.qwen3.Qwen3TtsCatalog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fully local Qwen3-TTS (LiteRT Compiled Model host loop) — 0.6B codec-LM,
 * multilingual, 24 kHz output.
 *
 * Bundle: litert-community/Qwen3-TTS-12Hz-0.6B-Base — Apache 2.0
 * Files: 3 .tflite graphs + fp16/fp32 host tables + vocab/merges + speaker.
 *
 * One-shot synthesis (no streaming): the host-side interleaved loop is
 * blocking and non-interruptible mid-frame; runs on Dispatchers.IO.
 */
class Qwen3TtsProvider : TTSProvider<TTSProviderSetting.Qwen3Tts> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Qwen3Tts,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        val directory = File(providerSetting.modelPath)
        require(directory.isDirectory) {
            "Qwen3 TTS model dir does not exist: ${directory.absolutePath}. " +
                "Download it from ${Qwen3TtsCatalog.ENTRIES.first().sourceUrl}"
        }

        val text = request.text.trim()
        if (text.isEmpty()) {
            emit(emptyLastChunk())
            return@flow
        }

        val engine = Qwen3TtsEngine(directory)
        try {
            val result = engine.synthesize(
                text = text,
                language = providerSetting.language,
                progress = object : Qwen3TtsEngine.Progress {
                    override fun onFrame(frame: Int) = Unit
                },
            )
            if (result.audio.isEmpty()) {
                emit(emptyLastChunk())
                return@flow
            }
            val pcmBytes = FloatArrayToPcm16(result.audio)
            emit(
                AudioChunk(
                    data = pcmBytes,
                    format = AudioFormat.PCM,
                    sampleRate = Qwen3TtsEngine.SAMPLE_RATE,
                    isLast = true,
                    metadata = mapOf(
                        "provider" to "qwen3-tts",
                        "language" to providerSetting.language,
                        "frames" to result.frames.toString(),
                    ),
                )
            )
        } finally {
            engine.close()
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
        sampleRate = Qwen3TtsEngine.SAMPLE_RATE,
        isLast = true,
        metadata = mapOf("provider" to "qwen3-tts"),
    )
}
