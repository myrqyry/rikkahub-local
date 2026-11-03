package me.rerere.locallm.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufTuningPolicyTest {
    @Test fun candidatePlanStartsWithExistingCpuDefault() {
        val candidates = GgufTuningPolicy.threadCandidates(8)
        assertEquals(8, candidates.first().threads)
        assertEquals(listOf(8, 2, 4, 6), candidates.map { it.threads })
        assertTrue(candidates.all { it.flashAttention })
        assertEquals(listOf(1), GgufTuningPolicy.threadCandidates(1).map { it.threads })
        assertEquals(listOf(3, 2, 1), GgufTuningPolicy.threadCandidates(3).map { it.threads })
    }

    @Test fun winnerComparesRealLatencyAndOutputRateWithoutInventingTokens() {
        val samples = listOf(
            GgufTuningSample(GgufTuningConfig(8), 200.0, 120.0, 145),
            GgufTuningSample(GgufTuningConfig(4), 135.0, 165.0, 154),
            GgufTuningSample(GgufTuningConfig(2), 310.0, 95.0, 151),
        )
        assertEquals(4, GgufTuningPolicy.choose(samples).config.threads)
    }

    @Test fun shortOrNonfiniteOutputsCannotBecomeWinningSamples() {
        assertThrows(IllegalArgumentException::class.java) {
            GgufTuningPolicy.choose(listOf(GgufTuningSample(GgufTuningConfig(4), 120.0, 1000.0, 12)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GgufTuningPolicy.choose(listOf(GgufTuningSample(GgufTuningConfig(4), Double.NaN, 20.0, 98)))
        }
    }
}
