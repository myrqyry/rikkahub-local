package me.rerere.asr

import org.junit.Assert.*
import org.junit.Test

class NemotronAudioChunkerTest {
    @Test fun `chunks preserve every PCM sample including stop tail`() {
        val emitted = mutableListOf<FloatArray>()
        val chunker = NemotronAudioChunker(4) { emitted.add(it) }
        chunker.add(shortArrayOf(32767, 0, -32768), 3)
        chunker.add(shortArrayOf(16384, 8192, -8192), 3)
        chunker.finish()
        assertEquals(listOf(4, 2), emitted.map { it.size })
        assertArrayEquals(floatArrayOf(32767 / 32768f, 0f, -1f, .5f), emitted[0], 0.00001f)
        assertArrayEquals(floatArrayOf(.25f, -.25f), emitted[1], 0.00001f)
    }

    @Test fun `full chunk does not create empty final feed`() {
        val emitted = mutableListOf<FloatArray>()
        val chunker = NemotronAudioChunker(2) { emitted.add(it) }
        chunker.add(shortArrayOf(1, 2), 2)
        chunker.finish()
        assertEquals(listOf(2), emitted.map { it.size })
    }
}
