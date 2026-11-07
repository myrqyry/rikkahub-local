package me.rerere.tts.controller

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmStreamPumpTest {
    @Test fun firstFrameIsDeliveredBeforeSynthesisCompletes() = runBlocking {
        var finished = false
        val data = flow {
            emit(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 24000))
            assertTrue("first frame should be consumed before the producer continues", finished)
            emit(AudioChunk(ByteArray(0), AudioFormat.PCM, 24000, isLast = true))
        }
        val frames = mutableListOf<Int>()
        PcmStreamPump.consume(data) { bytes, hz ->
            assertEquals(24000, hz)
            frames.add(bytes.size)
            finished = true
        }
        assertEquals(listOf(2), frames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleRateChangeFailsExplicitly() = runBlocking {
        PcmStreamPump.consume(flow {
            emit(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 24000))
            emit(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 16000))
        }) { _, _ -> }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPcmFrameFailsExplicitly() = runBlocking {
        PcmStreamPump.consume(flow {
            emit(AudioChunk(byteArrayOf(1, 0), AudioFormat.MP3, 24000))
        }) { _, _ -> }
    }

    @Test(expected = CancellationException::class)
    fun canceledConsumerStopsBeforeLaterFrames() = runBlocking {
        var consumed = 0
        PcmStreamPump.consume(flow {
            emit(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 24000))
            emit(AudioChunk(byteArrayOf(2, 0), AudioFormat.PCM, 24000))
        }) { _, _ ->
            consumed++
            if (consumed == 1) throw CancellationException("Stopped")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun oddPcmFrameFailsExplicitly() = runBlocking {
        PcmStreamPump.consume(flow {
            emit(AudioChunk(byteArrayOf(1), AudioFormat.PCM, 24000))
        }) { _, _ -> }
    }
}
