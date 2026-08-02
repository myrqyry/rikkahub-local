package me.rerere.tts.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketTtsConfigTest {
    @Test
    fun defaultsMatchTheReferenceEngine() {
        val config = PocketTtsConfig()
        assertEquals(4, config.flowSteps)
        assertEquals(1000, config.maxFrames)
        assertEquals(0, config.framesAfterEos)
        assertEquals(0.8f, config.temperature)
        assertEquals(0.5f, config.eosThreshold)
        assertEquals(4, config.intraThreads)
    }

    @Test
    fun rejectsOutOfBoundsFlowSteps() {
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(flowSteps = 0) }
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(flowSteps = 33) }
    }

    @Test
    fun rejectsOutOfBoundsMaxFrames() {
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(maxFrames = 0) }
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(maxFrames = 1001) }
    }

    @Test
    fun rejectsOutOfBoundsFramesAfterEos() {
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(framesAfterEos = -1) }
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(framesAfterEos = 51) }
    }

    @Test
    fun rejectsNonFiniteOrOutOfBoundsTemperature() {
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(temperature = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(temperature = 10.1f) }
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(temperature = Float.NaN) }
    }

    @Test
    fun rejectsOutOfBoundsIntraThreads() {
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(intraThreads = 0) }
        assertThrows(IllegalArgumentException::class.java) { PocketTtsConfig(intraThreads = 65) }
    }

    @Test
    fun negativeSeedIsRandomDevice() {
        val random = PocketTtsConfig(seed = -1)
        assertTrue(random.seed < 0)
        val deterministic = PocketTtsConfig(seed = 42)
        assertEquals(42, deterministic.seed)
    }
}
