package me.rerere.tts.matcha

import kotlin.math.ceil
import kotlin.math.exp
import kotlinx.serialization.json.jsonArray

data class MatchaEncodedText(
    val embeddings: FloatArray,
    val mask: FloatArray,
    val phonemeCount: Int,
)

data class MatchaRegulatedText(
    val mu: FloatArray,
    val mask: FloatArray,
    val frameCount: Int,
)

class MatchaTtsPreprocessor(
    private val bundle: MatchaTtsBundle,
) {
    private val symbols = bundle.config["symbols"]!!.jsonArray.map { it.toString().trim('"') }
    private val symbolIds = symbols.withIndex().associate { it.value to it.index }
    private val maxText = bundle.config["MAX_TEXT"]?.toString()?.toInt() ?: 256
    private val maxMel = bundle.config["MAX_MEL"]?.toString()?.toInt() ?: 512
    private val channels = bundle.config["n_channels"]?.toString()?.toInt() ?: 192
    private val embedding = bundle.embeddingFile.readBytes().let { bytes ->
        require(bytes.size == 178 * channels * 4) { "Invalid Matcha embedding table size" }
        FloatArray(bytes.size / 4) { index ->
            val offset = index * 4
            java.nio.ByteBuffer.wrap(bytes, offset, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).float
        }
    }

    fun encode(g2p: MatchaTtsG2p, text: String): MatchaEncodedText {
        val phonemes = g2p.phonemize(text).mapNotNull { symbolIds[it.toString()] }
        require(phonemes.isNotEmpty()) { "Matcha TTS produced no phonemes" }
        require(phonemes.size * 2 + 1 <= maxText) { "Matcha TTS text is too long" }
        val ids = IntArray(phonemes.size * 2 + 1)
        phonemes.forEachIndexed { index, id -> ids[index * 2 + 1] = id }
        val mask = FloatArray(maxText)
        val active = ids.size
        for (index in 0 until active) mask[index] = 1f
        val embeddings = FloatArray(maxText * channels)
        ids.forEachIndexed { index, id ->
            val source = id.coerceIn(0, 177) * channels
            embedding.copyInto(embeddings, index * channels, source, source + channels)
        }
        return MatchaEncodedText(embeddings, mask, active)
    }

    fun regulate(encoded: MatchaEncodedText, mu: FloatArray, logw: FloatArray, durationScale: Float): MatchaRegulatedText {
        val durations = IntArray(encoded.phonemeCount) { index ->
            ceil(exp(logw[index]) * durationScale * lengthScale).toInt().coerceAtLeast(1)
        }
        val frameCount = durations.sum().coerceIn(1, maxMel)
        val regulated = FloatArray(80 * maxMel)
        var frame = 0
        durations.forEachIndexed { source, duration ->
            repeat(duration) {
                if (frame < frameCount) {
                    for (channel in 0 until 80) {
                        regulated[channel * maxMel + frame] = mu[channel * maxText + source]
                    }
                    frame++
                }
            }
        }
        val mask = FloatArray(maxMel) { if (it < frameCount) 1f else 0f }
        return MatchaRegulatedText(regulated, mask, frameCount)
    }

    companion object {
        private const val lengthScale = 0.95f
    }
}
