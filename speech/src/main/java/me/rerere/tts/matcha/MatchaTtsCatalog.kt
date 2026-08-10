package me.rerere.tts.matcha

data class MatchaTtsCatalogEntry(
    val displayName: String,
    val modelId: String,
    val description: String,
    val sizeBytes: Long,
    val license: String,
    val sampleRate: Int = 22050,
) {
    val sourceUrl: String get() = "https://huggingface.co/$modelId"

    fun resolveFileUrl(file: String): String =
        "https://huggingface.co/$modelId/resolve/main/$file"

    fun downloadPairs(): List<Pair<String, String>> =
        MatchaTtsBundle.requiredFiles.map { resolveFileUrl(it) to it }
}

object MatchaTtsCatalog {
    val ENTRIES = listOf(
        MatchaTtsCatalogEntry(
            displayName = "Matcha-TTS (LiteRT)",
            modelId = "litert-community/Matcha-TTS",
            description = "English single-voice flow-matching TTS at 22.05 kHz. " +
                "Uses GPU text encoding and vocoding with a CPU decoder.",
            sizeBytes = 0L,
            license = "mit",
        ),
    )

    fun findByModelId(modelId: String): MatchaTtsCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }
}
