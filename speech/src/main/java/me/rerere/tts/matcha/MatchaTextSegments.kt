package me.rerere.tts.matcha

/**
 * A Matcha graph holds at most 256 interspersed phoneme positions and 512
 * mel frames (~5.9 s at 22.05 kHz). Keep each synthesis piece short so normal
 * 160-character chat chunks don't overflow the fixed model window.
 *
 * This intentionally chunks conservatively rather than pretending character
 * count is a precise phoneme/duration prediction.
 */
internal fun splitMatchaText(text: String, maxLength: Int = 48): List<String> {
    require(maxLength > 0)
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.length <= maxLength) return listOf(trimmed)

    val segments = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) {
            segments.add(current.toString())
            current.clear()
        }
    }
    for (word in trimmed.split(Regex("\\s+"))) {
        if (word.isEmpty()) continue
        if (word.length > maxLength) {
            flush()
            segments.addAll(word.chunked(maxLength))
            continue
        }
        val nextSize = current.length + if (current.isEmpty()) 0 else 1
        if (nextSize + word.length > maxLength) flush()
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    flush()
    return segments
}
