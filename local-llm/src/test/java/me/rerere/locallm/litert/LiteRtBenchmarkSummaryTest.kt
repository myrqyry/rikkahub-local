package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteRtBenchmarkSummaryTest {
    private fun sample(
        runMs: Double,
        ttft: Double,
        prefill: Double,
        decode: Double,
        prefillTokens: Int = 256,
        decodeTokens: Int = 64,
    ) = LiteRtBenchmarkSample(
        runDurationMs = runMs,
        firstTokenMs = ttft,
        prefillTokenCount = prefillTokens,
        decodeTokenCount = decodeTokens,
        prefillTokensPerSecond = prefill,
        decodeTokensPerSecond = decode,
    )

    @Test
    fun `reports measured wall duration separately for first and later runs`() {
        val result = LiteRtBenchmarkSummary.from(
            listOf(
                sample(3000.0, 200.0, 120.0, 30.0),
                sample(800.0, 180.0, 150.0, 35.0),
                sample(1000.0, 220.0, 110.0, 40.0),
            ),
        )
        assertEquals(3, result.runs)
        assertEquals(3000.0, result.firstRunDurationMs, 0.001)
        assertEquals(900.0, result.laterRunMedianMs!!, 0.001)
        assertEquals(200.0, result.firstTokenMedianMs!!, 0.001)
        assertEquals(120.0, result.prefillMedianTokensPerSecond!!, 0.001)
        assertEquals(35.0, result.decodeMedianTokensPerSecond!!, 0.001)
    }

    @Test
    fun `one run cannot report later-run median or unavailable native token rates`() {
        val result = LiteRtBenchmarkSummary.from(
            listOf(sample(1000.0, 0.0, 100.0, 20.0, prefillTokens = 0)),
        )
        assertEquals(1000.0, result.firstRunDurationMs, 0.001)
        assertNull(result.laterRunMedianMs)
        assertNull(result.firstTokenMedianMs)
        assertNull(result.prefillMedianTokensPerSecond)
        assertEquals(20.0, result.decodeMedianTokensPerSecond!!, 0.001)
    }

    @Test
    fun `rejects empty and nonsensical timing and throughput data`() {
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtBenchmarkSummary.from(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtBenchmarkSummary.from(listOf(sample(1.0, 2.0, Double.NaN, 3.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtBenchmarkSummary.from(listOf(sample(-1.0, 2.0, 30.0, 40.0)))
        }
    }
}
