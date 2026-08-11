package me.rerere.asr.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WhisperShapeLayoutTest {
    @Test
    fun baseLayoutIsDerived() {
        val shapes = deriveWhisperShapes(
            encOutputSize = 1 * 1500 * 512,
            decodeOutputs = listOf(
                intArrayOf(1, 1, 128, 512) to 1 * 1 * 128 * 512 * 4, // KV cache
                intArrayOf(128, 51865) to 128 * 51865 * 4,           // logits
            ),
        )
        assertEquals(768_000, shapes.encOutputSize)
        assertEquals(128, shapes.maxTokens)
        assertEquals(51_865, shapes.vocabSize)
        assertEquals(1 * 1 * 128 * 512, shapes.kvFloats)
    }

    @Test
    fun tinyLayoutIsDerived() {
        val shapes = deriveWhisperShapes(
            encOutputSize = 1 * 1500 * 384,
            decodeOutputs = listOf(
                intArrayOf(128, 51865) to 128 * 51865 * 4,           // logits first
                intArrayOf(1, 1, 128, 384) to 1 * 1 * 128 * 384 * 4, // KV cache
            ),
        )
        assertEquals(576_000, shapes.encOutputSize)
        assertEquals(128, shapes.maxTokens)
        assertEquals(51_865, shapes.vocabSize)
        assertEquals(1 * 1 * 128 * 384, shapes.kvFloats)
    }

    @Test
    fun malformedDecodeSignatureIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            deriveWhisperShapes(
                encOutputSize = 768_000,
                decodeOutputs = listOf(intArrayOf(128, 51865) to 128 * 51865 * 4),
            )
        }
    }
}
