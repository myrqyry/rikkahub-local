package me.rerere.tts.matcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatchaTtsConfigTest {
    @Test
    fun defaultsMatchReferenceSettings() {
        val config = MatchaTtsConfig()

        assertEquals(1.0f, config.speechSpeed)
        assertEquals(1.0f, config.durationScale)
        assertEquals(10, config.flowSteps)
        assertEquals(null, config.seed)
    }

    @Test
    fun effectiveDurationCombinesSpeedAndScale() {
        assertEquals(
            0.75f,
            MatchaTtsConfig(speechSpeed = 2.0f, durationScale = 1.5f)
                .effectiveDurationScale,
        )
    }

    @Test
    fun rejectsInvalidRuntimeSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchaTtsConfig(speechSpeed = 0.49f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatchaTtsConfig(durationScale = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatchaTtsConfig(flowSteps = 31)
        }
    }
}
