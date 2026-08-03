package me.rerere.tts.pocket

/**
 * Curated Pocket TTS model list. Single-file entries resolve to a 9-file ONNX bundle
 * via HF conventions — but the app download must fetch all 9 files. This catalog
 * gives the UI a display name, rough size, license, and the repo URL users can open.
 *
 * Proven runtime: soniqo/Pocket-TTS-100M-ONNX-INT8 (125 MB, ONNX Runtime Android 1.25,
 * 24 kHz, 80 ms frames, ~376 MiB RSS on S23 Ultra, CC BY 4.0).
 */
data class PocketTtsCatalogEntry(
    val displayName: String,
    val modelId: String,
    val description: String,
    val sizeBytes: Long,
    val license: String,
    val requiredFiles: List<String> = PocketTtsBundle.requiredFiles,
) {
    fun resolveFileUrl(file: String): String =
        "https://huggingface.co/$modelId/resolve/main/$file"

    val sourceUrl: String get() = "https://huggingface.co/$modelId"
}

object PocketTtsCatalog {
    val ENTRIES: List<PocketTtsCatalogEntry> = listOf(
        PocketTtsCatalogEntry(
            displayName = "Pocket TTS 100M ONNX INT8",
            modelId = "soniqo/Pocket-TTS-100M-ONNX-INT8",
            description = "Kyutai 100M based, 5-graph INT8 ONNX bundle. English, fixed alba voice, streaming 24 kHz, ~125 MB. Proven Android via ONNX Runtime. CC BY 4.0.",
            sizeBytes = 131_000_000,
            license = "cc-by-4.0",
        ),
        PocketTtsCatalogEntry(
            displayName = "Pocket TTS (official, no voice cloning)",
            modelId = "kyutai/pocket-tts-without-voice-cloning",
            description = "Upstream PyTorch from kyutai-labs/pocket-tts. Requires ONNX export (use KevinAHM/pocket-tts-onnx-export). English only, CC BY 4.0, gated.",
            sizeBytes = 400_000_000,
            license = "cc-by-4.0",
        ),
    )

    fun findByModelId(modelId: String): PocketTtsCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }
}
