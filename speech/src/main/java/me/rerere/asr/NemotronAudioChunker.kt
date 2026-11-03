package me.rerere.asr

/** Converts sequential mono PCM16 reads into exact, non-overlapping 16k float chunks.
 * The final shorter chunk must be flushed before stream_finalize().
 */
internal class NemotronAudioChunker(
    private val chunkSamples: Int,
    private val emit: (FloatArray) -> Unit,
) {
    init { require(chunkSamples > 0) }
    private var pending = FloatArray(chunkSamples)
    private var count = 0

    fun add(samples: ShortArray, length: Int) {
        require(length in 0..samples.size)
        for (i in 0 until length) {
            pending[count++] = samples[i] / 32768f
            if (count == chunkSamples) flush()
        }
    }

    fun finish() {
        if (count > 0) flush()
    }

    private fun flush() {
        emit(pending.copyOf(count))
        count = 0
    }
}
