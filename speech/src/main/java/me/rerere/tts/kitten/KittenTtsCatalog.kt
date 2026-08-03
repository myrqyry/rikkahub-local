package me.rerere.tts.kitten

/** Curated Kitten TTS models for the settings Download/Source UI. */
data class KittenTtsCatalogEntry(
    val displayName: String,
    val modelId: String,
    val modelFile: String,
    val voicesFile: String,
    val description: String,
    val sizeBytes: Long,
    val license: String,
    val sampleRate: Int = 24000,
) {
    fun resolveFileUrl(file: String): String =
        "https://huggingface.co/$modelId/resolve/main/$file"

    val sourceUrl: String get() = "https://huggingface.co/$modelId"

    val requiredFiles: List<String> get() = listOf(modelFile, voicesFile)
}

object KittenTtsCatalog {
    val ENTRIES: List<KittenTtsCatalogEntry> = listOf(
        KittenTtsCatalogEntry(
            displayName = "Kitten TTS Nano 0.1",
            modelId = "KittenML/kitten-tts-nano-0.1",
            modelFile = "kitten_tts_nano_v0_1.onnx",
            voicesFile = "voices.npz",
            description = "Ultra-lightweight 15M param TTS. Tiny (<25 MB), CPU-only, 8 premium voices, 24 kHz. English. Apache 2.0. Any device. Great first pick.",
            sizeBytes = 25_000_000,
            license = "apache-2.0",
        ),
    )

    fun findByModelId(modelId: String): KittenTtsCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }
}
