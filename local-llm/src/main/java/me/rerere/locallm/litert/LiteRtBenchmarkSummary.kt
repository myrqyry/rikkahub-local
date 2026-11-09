package me.rerere.locallm.litert

/**
 * SDK token counts and speeds, plus an independently observed monotonic wall duration.
 * The SDK's initTimeInSecond must NOT be shown as wall-clock initialization: on the
 * Pixel 10 Pro it exceeded the entire measured benchmark call by several seconds.
 */
data class LiteRtBenchmarkSample(
    val runDurationMs: Double,
    val firstTokenMs: Double,
    val prefillTokenCount: Int,
    val decodeTokenCount: Int,
    val prefillTokensPerSecond: Double,
    val decodeTokensPerSecond: Double,
)

data class LiteRtBenchmarkSummary(
    val runs: Int,
    val firstRunDurationMs: Double,
    val laterRunMedianMs: Double?,
    val firstTokenMedianMs: Double?,
    val prefillMedianTokensPerSecond: Double?,
    val decodeMedianTokensPerSecond: Double?,
) {
    companion object {
        fun from(samples: List<LiteRtBenchmarkSample>): LiteRtBenchmarkSummary {
            require(samples.isNotEmpty()) { "At least one native benchmark run is required" }
            require(samples.all { sample ->
                listOf(
                    sample.runDurationMs, sample.firstTokenMs,
                    sample.prefillTokensPerSecond, sample.decodeTokensPerSecond,
                ).all { it.isFinite() && it >= 0.0 } &&
                    sample.prefillTokenCount >= 0 && sample.decodeTokenCount >= 0
            }) { "Invalid native benchmark measurements" }

            return LiteRtBenchmarkSummary(
                runs = samples.size,
                firstRunDurationMs = samples.first().runDurationMs,
                laterRunMedianMs = medianIfMeasured(samples.drop(1).map { it.runDurationMs }),
                firstTokenMedianMs = medianIfMeasured(samples.map { it.firstTokenMs }),
                prefillMedianTokensPerSecond =
                    if (samples.all { it.prefillTokenCount > 0 })
                        medianIfMeasured(samples.map { it.prefillTokensPerSecond })
                    else null,
                decodeMedianTokensPerSecond =
                    if (samples.all { it.decodeTokenCount > 0 })
                        medianIfMeasured(samples.map { it.decodeTokensPerSecond })
                    else null,
            )
        }

        private fun medianIfMeasured(values: List<Double>): Double? {
            if (values.isEmpty() || values.any { !it.isFinite() || it <= 0.0 }) return null
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle]
            else (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
