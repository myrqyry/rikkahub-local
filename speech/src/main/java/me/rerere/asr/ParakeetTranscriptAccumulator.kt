package me.rerere.asr

/** MediaPipe returns newly emitted segments. Interim revisions overlap their final segments. */
data class ParakeetSegment(
    val startMs: Long,
    val endMs: Long,
    val transcript: String,
    val isFinal: Boolean,
)

class ParakeetTranscriptAccumulator {
    private var segments = emptyList<ParakeetSegment>()

    fun add(incoming: List<ParakeetSegment>): String {
        for (segment in incoming) {
            if (segment.transcript.isBlank()) continue
            val kept = segments.filter { old ->
                old.endMs <= segment.startMs || old.startMs >= segment.endMs ||
                    (old.isFinal && !segment.isFinal)
            }
            val protectedByFinal = kept.any { old ->
                old.isFinal && old.startMs < segment.endMs && segment.startMs < old.endMs
            }
            segments = if (!segment.isFinal && protectedByFinal) kept
                else kept + segment
            segments = segments.sortedBy { it.startMs }
        }
        return text()
    }

    fun text(): String = segments.map { it.transcript.trim() }
        .filter { it.isNotEmpty() }.joinToString(" ").trim()
}
