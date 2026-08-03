package me.rerere.tts.qwen3

/**
 * Qwen3-TTS-12Hz-0.6B-Base (on-device LiteRT conversion) — download catalog.
 *
 * Source: https://huggingface.co/litert-community/Qwen3-TTS-12Hz-0.6B-Base
 * License: Apache-2.0 (Alibaba Qwen team; converted artifacts retain it)
 *
 * The engine reads files by bare name from its model directory, so remote
 * subpaths (tables/…, voices/…) are flattened on download.
 */
data class Qwen3TtsCatalogEntry(
    val displayName: String,
    val modelId: String,
    val description: String,
    val sizeBytes: Long,
    val license: String,
    val sampleRate: Int = 24000,
) {
    /** Remote path -> local flattened filename. */
    private data class ModelFile(val remote: String, val local: String)

    private val files = listOf(
        ModelFile("talker_int4.tflite", "talker_int4.tflite"),
        ModelFile("mtp_fp32.tflite", "mtp_fp32.tflite"),
        ModelFile("codec_decoder_fp32.tflite", "codec_decoder_fp32.tflite"),
        ModelFile("tables/codec_embedding_fp32.npy", "codec_embedding_fp32.npy"),
        ModelFile("tables/mtp_embeddings_fp16.npy", "mtp_embeddings_fp16.npy"),
        ModelFile("tables/text_embedding_fp16.npy", "text_embedding_fp16.npy"),
        ModelFile("tables/text_projection_fp32.npz", "text_projection_fp32.npz"),
        ModelFile("voices/demo_speaker.npy", "demo_speaker.npy"),
        ModelFile("vocab.json", "vocab.json"),
        ModelFile("merges.txt", "merges.txt"),
    )

    /** Local filenames the engine requires in its model directory. */
    val requiredFiles: List<String> get() = files.map { it.local }

    fun resolveFileUrl(remote: String): String =
        "https://huggingface.co/$modelId/resolve/main/$remote"

    /** Remote subpath for a given local filename (for manual URL entry). */
    fun remoteForLocal(local: String): String? =
        files.firstOrNull { it.local == local }?.remote

    /** (url, localFileName) pairs for the downloader. */
    fun downloadPairs(): List<Pair<String, String>> =
        files.map { resolveFileUrl(it.remote) to it.local }

    val sourceUrl: String get() = "https://huggingface.co/$modelId"
}

object Qwen3TtsCatalog {
    val ENTRIES: List<Qwen3TtsCatalogEntry> = listOf(
        Qwen3TtsCatalogEntry(
            displayName = "Qwen3-TTS-12Hz-0.6B-Base (LiteRT)",
            modelId = "litert-community/Qwen3-TTS-12Hz-0.6B-Base",
            description = "Multilingual codec-LM TTS, 0.6B, 3-second voice cloning, " +
                "24 kHz. Three LiteRT graphs (talker int4 + MTP + codec decoder) " +
                "orchestrated host-side. ~1.4 GB, CPU-only, not realtime (RTF ~6.7 " +
                "on Pixel 8a). Apache 2.0.",
            sizeBytes = 1_400_000_000,
            license = "apache-2.0",
        ),
    )

    fun findByModelId(modelId: String): Qwen3TtsCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }
}
