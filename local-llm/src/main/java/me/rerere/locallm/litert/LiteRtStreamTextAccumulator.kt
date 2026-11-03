package me.rerere.locallm.litert

/**
 * LiteRT-LM MessageCallback.onMessage reports a new fragment, while our provider
 * emits cumulative snapshots and converts them to UI deltas afterward.
 * Keep this per native turn, never across turns or cancellations.
 */
internal class LiteRtStreamTextAccumulator {
    private val response = StringBuilder()

    fun append(chunk: String): String {
        response.append(chunk)
        return response.toString()
    }
}
