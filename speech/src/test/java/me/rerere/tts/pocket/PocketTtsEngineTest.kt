package me.rerere.tts.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketTtsEngineTest {

    @Test
    fun `pocket constants match the mimi decoder contract`() {
        assertEquals(24000, PocketTtsEngine.SAMPLE_RATE)
        assertEquals(32, PocketTtsEngine.LATENT_DIM)
        assertEquals(1024, PocketTtsEngine.EMBED_DIM)
        assertEquals(1920, PocketTtsEngine.SAMPLES_PER_FRAME)
    }

    @Test
    fun `audio sample count is frames times 1920`() {
        assertEquals(1 * 1920, PocketTtsEngine.expectedAudioSamples(1))
        assertEquals(3 * 1920, PocketTtsEngine.expectedAudioSamples(3))
        assertEquals(0, PocketTtsEngine.expectedAudioSamples(0))
    }

    @Test
    fun `graphs exposing too few inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PocketTtsEngine.requireInputs(listOf("a"), minimum = 4, graph = "lm_main")
        }
        assertEquals(
            listOf("a", "b", "c", "d"),
            PocketTtsEngine.requireInputs(listOf("a", "b", "c", "d"), minimum = 4, graph = "lm_main"),
        )
    }

    @Test
    fun `primary output is the first declared output`() {
        assertEquals("conditioning", PocketTtsEngine.primaryOutput(listOf("conditioning", "eos_logit")))
        assertThrows(IllegalStateException::class.java) {
            PocketTtsEngine.primaryOutput(emptyList<String>())
        }
    }

    @Test
    fun `noise frame draws one gaussian per latent dim scaled by stddev`() {
        val rng = java.util.Random(42L)
        val frame = PocketTtsEngine.noiseFrame(rng, stddev = 0.5f, size = 32)
        assertEquals(32, frame.size)
        frame.forEach { assertTrue(it.isFinite()) }
    }

    @Test
    fun `noise frame is deterministic for a fixed seed`() {
        val a = PocketTtsEngine.noiseFrame(java.util.Random(7L), 0.8f, 32)
        val b = PocketTtsEngine.noiseFrame(java.util.Random(7L), 0.8f, 32)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `flow euler step moves the latent along the flow direction`() {
        val latent = FloatArray(32) { 1.0f }
        val direction = FloatArray(32) { 2.0f }
        val result = PocketTtsEngine.flowEuler(latent, direction, delta = 0.25f)
        assertEquals(32, result.size)
        result.forEach { assertEquals(1.5f, it, 1e-6f) }
    }

    @Test
    fun `flow euler step with zero delta returns the latent unchanged`() {
        val latent = FloatArray(32) { it.toFloat() }
        val result = PocketTtsEngine.flowEuler(latent, FloatArray(32) { 9.0f }, delta = 0.0f)
        assertTrue(latent.contentEquals(result))
    }
}
