package me.rerere.tts.controller

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat

/** Validates and forwards PCM16 mono frames without collecting the entire utterance. */
internal object PcmStreamPump {
    suspend fun consume(source: Flow<AudioChunk>, write: suspend (ByteArray, Int) -> Unit) {
        var expectedRate: Int? = null
        var ended = false
        source.collect { chunk ->
            require(!ended) { "Audio arrived after the final chunk" }
            require(chunk.format == AudioFormat.PCM) { "PCM streaming received ${chunk.format}" }
            val hz = requireNotNull(chunk.sampleRate) { "PCM stream requires sample rate" }
            require(hz in 8000..192000) { "Invalid PCM sample rate: $hz" }
            require(expectedRate == null || expectedRate == hz) { "PCM sample rate changed during stream" }
            expectedRate = hz
            require(chunk.data.size % 2 == 0) { "Truncated PCM16 frame" }
            if (chunk.data.isNotEmpty()) write(chunk.data, hz)
            if (chunk.isLast) ended = true
        }
    }
}
