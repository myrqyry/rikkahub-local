package me.rerere.tts.pocket

class PocketTtsSynthesizer private constructor() {

    enum class StateKind { LONG, BOOL, FLOAT }

    companion object {
        private const val EMBED_DIM = 1024
        private const val DEFAULT_LM_CACHE_LENGTH = 1000

        fun stateKind(type: String): StateKind = when {
            type.contains("int64") -> StateKind.LONG
            type.contains("bool") -> StateKind.BOOL
            else -> StateKind.FLOAT
        }

        fun zeroState(kind: StateKind, size: Int): Any = when (kind) {
            StateKind.LONG -> LongArray(size)
            StateKind.BOOL -> ByteArray(size)
            StateKind.FLOAT -> FloatArray(size)
        }

        fun lmCacheLength(shape: LongArray, default: Int = DEFAULT_LM_CACHE_LENGTH): Int {
            if (shape.size != 5) return default
            val cache = shape[2]
            return if (cache > 0) cache.toInt() else default
        }

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

        fun voiceTokens(shape: LongArray): Int {
            require(shape.size == 3 && shape[0] == 1L && shape[2] == EMBED_DIM.toLong()) {
                "Pocket TTS voice latents must be {1, voice_tokens, 1024}, got shape=${shape.joinToString()}"
            }
            return shape[1].toInt()
        }

        fun frameLimit(lmCacheLength: Int, voiceTokens: Int, tokenCount: Int, maxFrames: Int): Int {
            val remaining = lmCacheLength - voiceTokens - tokenCount
            require(remaining > 0) {
                "Pocket TTS text and voice conditioning exceed the $lmCacheLength token LM cache"
            }
            return minOf(maxFrames, remaining)
        }
    }
}
