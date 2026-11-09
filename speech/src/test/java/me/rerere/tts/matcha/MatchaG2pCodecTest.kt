package me.rerere.tts.matcha

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchaG2pCodecTest {
    @Test
    fun `word characters are repeated into padded input not output`() {
        val input = encodeG2pCharacters(
            word = "ab",
            charToIndex = mapOf("a" to 5, "b" to 6),
            repeats = 3,
            startId = 1,
            endId = 2,
            maxLength = 12,
        )
        assertArrayEquals(floatArrayOf(1f, 5f, 5f, 5f, 6f, 6f, 6f, 2f, 0f, 0f, 0f, 0f), input, 0f)
    }

    @Test
    fun `oversized word preserves end token and fixed graph size`() {
        val input = encodeG2pCharacters("a".repeat(100), mapOf("a" to 8), 3, 1, 2, 96)
        assertEquals(96, input.size)
        assertEquals(2f, input[94])
        assertEquals(0f, input[95])
    }

    @Test
    fun `CTC repeated frames collapse and blank separates identical phonemes`() {
        val names = mapOf(0 to "_", 1 to "<en_us>", 2 to "<end>", 3 to "k", 4 to "æ", 5 to "t")
        val ids = intArrayOf(1, 3, 3, 0, 3, 4, 4, 5, 2, 0, 0)
        val logits = FloatArray(ids.size * names.size) { -10f }
        ids.forEachIndexed { frame, id -> logits[frame * names.size + id] = 9f }
        assertEquals("kkæt", decodeG2pCtc(logits, names))
    }

    @Test
    fun `CTC output must not multiply every phoneme by char repeats`() {
        val names = mapOf(0 to "_", 1 to "<en_us>", 2 to "<end>", 3 to "h", 4 to "i")
        val ids = intArrayOf(1, 3, 3, 4, 4, 2, 0, 0)
        val logits = FloatArray(ids.size * names.size) { -4f }
        ids.forEachIndexed { frame, id -> logits[frame * names.size + id] = 5f }
        assertEquals("hi", decodeG2pCtc(logits, names))
    }
}
