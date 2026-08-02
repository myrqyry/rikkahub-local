package me.rerere.tts.pocket

class PocketTtsSynthesizer private constructor() {

    companion object {
        fun stateSize(shape: LongArray): Int =
            shape.fold(1L) { acc, dim -> acc * if (dim < 0) 0 else dim }.toInt().coerceAtLeast(0)

        fun stateInputName(outputName: String): String? =
            outputName
                .takeIf { it.startsWith("out_state_") }
                ?.removePrefix("out_state_")
                ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                ?.let { "state_$it" }

        fun shouldStopAfterEos(generatedFrames: Int, eosStep: Int?, framesAfterEos: Int): Boolean =
            eosStep != null && generatedFrames >= eosStep + framesAfterEos

        fun flowBuffers(steps: Int): List<Pair<Float, Float>> {
            val dt = 1.0f / steps
            return (0 until steps).map { j ->
                val start = j / steps.toFloat()
                Pair(start, start + dt)
            }
        }

        fun chunkPlan(frameCount: Int, chunkSize: Int): List<IntRange> =
            (0 until frameCount step chunkSize).map { i ->
                i until minOf(i + chunkSize, frameCount)
            }
    }
}
