package me.rerere.locallm.litert.image.bonsai

import org.junit.Assert.*
import org.junit.Test

class BonsaiHostParityTest {
    @Test fun patchPositionsFor256DoNotAliasTextPositions() {
        assertEquals(16, BonsaiMath.gridFor(256))
        val ids = BonsaiMath.imgIds(16)
        assertEquals(256 * 4, ids.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), ids.copyOfRange(0, 4), 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f, 0f), ids.copyOfRange(16 * 4, 16 * 4 + 4), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 255f), BonsaiMath.txtIds().copyOfRange(255 * 4, 256 * 4), 0f)
    }

    @Test fun seededNoiseReproducibleAcrossOutputSizes() {
        val short = BonsaiMath.noise(7L, 16 * 16)
        val long = BonsaiMath.noise(7L, 32 * 32)
        assertEquals(16 * 16 * 128, short.size)
        assertArrayEquals(short, long.copyOfRange(0, short.size), 0f)
        assertFalse(short.contentEquals(BonsaiMath.noise(8L, 16 * 16)))
    }

    @Test fun flowScheduleHasExpectedBoundaryValues() {
        val sigma = BonsaiMath.sigmas(4, 256)
        assertEquals(5, sigma.size)
        assertEquals(1f, sigma.first(), 1e-6f)
        assertEquals(0f, sigma.last(), 1e-6f)
        assertTrue((0 until sigma.lastIndex).all { sigma[it] >= sigma[it + 1] })
    }

    @Test fun unpatchifyIsShapeChecked() {
        assertThrows(IllegalArgumentException::class.java) {
            BonsaiMath.unpatchify(FloatArray(1), FloatArray(128), FloatArray(128), 16)
        }
        val a = FloatArray(16 * 16 * 128) { 1f }
        val z = BonsaiMath.unpatchify(a, FloatArray(128) { 2f }, FloatArray(128) { 3f }, 16)
        assertEquals(32 * 32 * 32, z.size)
        assertTrue(z.all { it == 5f })
    }
}
