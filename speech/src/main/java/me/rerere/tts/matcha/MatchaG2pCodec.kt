package me.rerere.tts.matcha

/**
 * Matcha's DeepPhonemizer graph expects a fixed [1,96] CTC input.
 *
 * char_repeats is the number of *input* copies of each character, not an
 * instruction to repeat decoded phonemes. Keep a trailing end token when a
 * word exceeds the fixed graph capacity.
 */
internal fun encodeG2pCharacters(
    word: String,
    charToIndex: Map<String, Int>,
    repeats: Int,
    startId: Int,
    endId: Int,
    maxLength: Int = 96,
): FloatArray {
    require(repeats > 0 && maxLength >= 2)
    val result = FloatArray(maxLength)
    result[0] = startId.toFloat()
    val maxCharacters = (maxLength - 2) / repeats
    var index = 1
    for (character in word.take(maxCharacters)) {
        val id = (charToIndex[character.toString()] ?: 0).toFloat()
        repeat(repeats) { result[index++] = id }
    }
    result[index] = endId.toFloat()
    return result
}

/**
 * CTC greedy decoding: select the highest logit per frame, collapse adjacent
 * identical indices, then discard blanks and language/control markers.
 * The padding after <end> must never become spurious extra phonemes.
 */
internal fun decodeG2pCtc(
    logits: FloatArray,
    indexToPhoneme: Map<Int, String>,
): String {
    val vocabulary = indexToPhoneme.size
    require(vocabulary > 0 && logits.size % vocabulary == 0) {
        "Unexpected Matcha G2P output shape"
    }
    return buildString {
        var previous = -1
        repeat(logits.size / vocabulary) { frame ->
            val offset = frame * vocabulary
            var best = 0
            for (id in 1 until vocabulary) {
                if (logits[offset + id] > logits[offset + best]) best = id
            }
            val label = indexToPhoneme[best].orEmpty()
            if (best != previous) {
                if (label == "<end>") return@buildString
                if (best != 0 && label != "_" && label != "<en_us>" && label != "<start>") {
                    append(label)
                }
            }
            previous = best
        }
    }
}
